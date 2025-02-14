/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vinyldns.api.domain.batch

import cats.data.NonEmptyList
import org.joda.time.DateTime
import vinyldns.api.VinylDNSConfig
import vinyldns.core.domain.{DomainValidationError, SingleChangeError}
import vinyldns.core.domain.DomainHelpers.ensureTrailingDot
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordData
import vinyldns.core.domain.record.RecordType._

final case class BatchChangeInput(
    comments: Option[String],
    changes: List[ChangeInput],
    ownerGroupId: Option[String] = None,
    scheduledTime: Option[DateTime] = None)

object BatchChangeInput {
  def apply(batchChange: BatchChange): BatchChangeInput = {
    val changes = batchChange.changes.map {
      case add: SingleAddChange => AddChangeInput(add)
      case del: SingleDeleteChange => DeleteChangeInput(del)
    }
    new BatchChangeInput(batchChange.comments, changes, batchChange.ownerGroupId)
  }
}

sealed trait ChangeInput {
  val inputName: String
  val typ: RecordType
  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange
}

final case class AddChangeInput(
    inputName: String,
    typ: RecordType,
    ttl: Option[Long],
    record: RecordData)
    extends ChangeInput {

  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange = {
    val knownTtl = ttl.getOrElse(VinylDNSConfig.defaultTtl)
    SingleAddChange(
      None,
      None,
      None,
      inputName,
      typ,
      knownTtl,
      record,
      SingleChangeStatus.NeedsReview,
      None,
      None,
      None,
      errors.toList.map(SingleChangeError(_))
    )
  }
}

final case class DeleteChangeInput(inputName: String, typ: RecordType) extends ChangeInput {
  def asNewStoredChange(errors: NonEmptyList[DomainValidationError]): SingleChange =
    SingleDeleteChange(
      None,
      None,
      None,
      inputName,
      typ,
      SingleChangeStatus.NeedsReview,
      None,
      None,
      None,
      errors.toList.map(SingleChangeError(_))
    )
}

object AddChangeInput {
  def apply(
      inputName: String,
      typ: RecordType,
      ttl: Option[Long],
      record: RecordData): AddChangeInput = {
    val transformName = typ match {
      case PTR => inputName
      case _ => ensureTrailingDot(inputName)
    }
    new AddChangeInput(transformName, typ, ttl, record)
  }

  def apply(sc: SingleAddChange): AddChangeInput =
    AddChangeInput(sc.inputName, sc.typ, Some(sc.ttl), sc.recordData)
}

object DeleteChangeInput {
  def apply(inputName: String, typ: RecordType): DeleteChangeInput = {
    val transformName = typ match {
      case PTR => inputName
      case _ => ensureTrailingDot(inputName)
    }
    new DeleteChangeInput(transformName, typ)
  }

  def apply(sc: SingleDeleteChange): DeleteChangeInput =
    DeleteChangeInput(sc.inputName, sc.typ)
}

object ChangeInputType extends Enumeration {
  type ChangeInputType = Value
  val Add, DeleteRecordSet = Value
}

final case class RejectBatchChangeInput(reviewComment: Option[String] = None)

final case class ApproveBatchChangeInput(reviewComment: Option[String] = None)
