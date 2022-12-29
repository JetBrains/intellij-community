// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationResult.Companion.serialize
import com.intellij.util.io.DataOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream


sealed class VfsOperation<T : Any>(val tag: VfsOperationTag, val result: OperationResult<T>) {
  /**
   * VALUE_SIZE_BYTES in nested classes -- size of all value fields in bytes excluding tag (includes result)
   */

  open suspend fun serializeValue(enumerator: SuspendDataEnumerator<String>): ByteArray = TODO() // make abstract

  sealed class RecordsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class AllocateRecord(result: OperationResult<Int>) : RecordsOperation<Int>(VfsOperationTag.REC_ALLOC, result) {
      companion object {
        const val VALUE_SIZE_BYTES = OperationResult.SIZE_BYTES

        suspend fun deserializeValue(data: ByteArray, enumerator: SuspendDataEnumerator<String>): AllocateRecord =
          DataInputStream(ByteArrayInputStream(data)).run {
            val result = readResult<Int>(enumerator)
            AllocateRecord(result)
          }
      }

      override suspend fun serializeValue(enumerator: SuspendDataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetAttributeRecordId(val fileId: Int, val recordId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_ATTR_REC_ID, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES

        suspend fun deserializeValue(data: ByteArray, enumerator: SuspendDataEnumerator<String>): SetAttributeRecordId =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            SetAttributeRecordId(fileId, recordId, result)
          }
      }

      override suspend fun serializeValue(enumerator: SuspendDataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(recordId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetContentRecordId(val fileId: Int, val recordId: Int, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_CONTENT_RECORD_ID, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES

        suspend fun deserializeValue(data: ByteArray, enumerator: SuspendDataEnumerator<String>): SetContentRecordId =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val recordId = readInt()
            val result = readResult<Boolean>(enumerator)
            SetContentRecordId(fileId, recordId, result)
          }
      }

      override suspend fun serializeValue(enumerator: SuspendDataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(recordId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }
  }

  sealed class ContentsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class WriteBytes(val recordId: Int, val fixedSize: Boolean, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_BYTES, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
      }
    }
  }

  sealed class AttributesOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class WriteAttribute(val fileId: Int, val attributeIdEnumerated: Int, val attrDataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : AttributesOperation<Unit>(VfsOperationTag.ATTR_WRITE_ATTR, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES

        suspend fun deserializeValue(data: ByteArray, enumerator: SuspendDataEnumerator<String>): WriteAttribute =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val attrIdEnumerated = readInt()
            val payloadRef = PayloadRef(readLong())
            val result = OperationResult.deserialize<Unit>(readInt()) {
              enumerator.valueOf(it) ?: throw IllegalStateException("corrupted enumerator storage")
            }
            WriteAttribute(fileId, attrIdEnumerated, payloadRef, result)
          }
      }

      override suspend fun serializeValue(enumerator: SuspendDataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(attributeIdEnumerated)
            writeLong(attrDataPayloadRef.value)
            writeInt(result.serialize { enumerator.enumerate(it) })
          }
          toByteArray()
        }
    }
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray, enumerator: SuspendDataEnumerator<String>): T =
      when (tag) {
        VfsOperationTag.NULL -> throw IllegalArgumentException("NULL descriptor is unexpected")

        VfsOperationTag.REC_ALLOC -> RecordsOperation.AllocateRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_ATTR_REC_ID -> RecordsOperation.SetAttributeRecordId.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_CONTENT_RECORD_ID -> RecordsOperation.SetContentRecordId.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_PARENT -> TODO()
        VfsOperationTag.REC_SET_NAME_ID -> TODO()
        VfsOperationTag.REC_SET_FLAGS -> TODO()
        VfsOperationTag.REC_PUT_LENGTH -> TODO()
        VfsOperationTag.REC_PUT_TIMESTAMP -> TODO()
        VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED -> TODO()
        VfsOperationTag.REC_FILL_RECORD -> TODO()
        VfsOperationTag.REC_CLEAN_RECORD -> TODO()
        VfsOperationTag.REC_SET_VERSION -> TODO()

        VfsOperationTag.ATTR_WRITE_ATTR -> AttributesOperation.WriteAttribute.deserializeValue(data, enumerator) as T
        VfsOperationTag.ATTR_DELETE_ATTRS -> TODO()
        VfsOperationTag.ATTR_SET_VERSION -> TODO()

        VfsOperationTag.CONTENT_WRITE_BYTES -> TODO()
        VfsOperationTag.CONTENT_WRITE_STREAM -> TODO()
        VfsOperationTag.CONTENT_WRITE_STREAM_2 -> TODO()
        VfsOperationTag.CONTENT_APPEND_STREAM -> TODO()
        VfsOperationTag.CONTENT_REPLACE_BYTES -> TODO()
        VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD -> TODO()
        VfsOperationTag.CONTENT_RELEASE_RECORD -> TODO()
        VfsOperationTag.CONTENT_SET_VERSION -> TODO()
      }

    private suspend inline fun <reified T : Any> DataInputStream.readResult(enumerator: SuspendDataEnumerator<String>) =
      OperationResult.deserialize<T>(readInt()) {
        enumerator.valueOf(it) ?: throw IllegalStateException("corrupted enumerator storage")
      }

    private suspend inline fun <reified T : Any> OperationResult<T>.serialize(enumerator: SuspendDataEnumerator<String>): Int =
      serialize { enumerator.enumerate(it) }
  }
}

