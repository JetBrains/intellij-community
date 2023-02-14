// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationResult.Companion.serialize
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DataOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream


sealed class VfsOperation<T : Any>(val tag: VfsOperationTag, val result: OperationResult<T>) {
  /**
   * VALUE_SIZE_BYTES in nested classes -- size of all value fields in bytes excluding tag (includes result)
   */

  abstract fun serializeValue(enumerator: DataEnumerator<String>): ByteArray

  sealed class RecordsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class AllocateRecord(result: OperationResult<Int>) : RecordsOperation<Int>(VfsOperationTag.REC_ALLOC, result) {
      companion object {
        const val VALUE_SIZE_BYTES = OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>): AllocateRecord =
          DataInputStream(ByteArrayInputStream(data)).run {
            val result = readResult<Int>(enumerator)
            AllocateRecord(result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
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

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>): SetAttributeRecordId =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            SetAttributeRecordId(fileId, recordId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
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

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>): SetContentRecordId =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val recordId = readInt()
            val result = readResult<Boolean>(enumerator)
            SetContentRecordId(fileId, recordId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(recordId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetParent(val fileId: Int, val parentId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_PARENT, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>): SetParent =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val parentId = readInt()
            val result = readResult<Unit>(enumerator)
            SetParent(fileId, parentId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(parentId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetNameId(val fileId: Int, val nameId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_NAME_ID, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val nameId = readInt()
            val result = readResult<Unit>(enumerator)
            SetNameId(fileId, nameId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(nameId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetFlags(val fileId: Int, val flags: Int, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_FLAGS, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val flags = readInt()
            val result = readResult<Boolean>(enumerator)
            SetFlags(fileId, flags, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(flags)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetLength(val fileId: Int, val length: Long, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_LENGTH, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + Long.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val length = readLong()
            val result = readResult<Boolean>(enumerator)
            SetLength(fileId, length, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeLong(length)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetTimestamp(val fileId: Int, val timestamp: Long, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_TIMESTAMP, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + Long.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val timestamp = readLong()
            val result = readResult<Boolean>(enumerator)
            SetTimestamp(fileId, timestamp, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeLong(timestamp)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class MarkRecordAsModified(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val result = readResult<Unit>(enumerator)
            MarkRecordAsModified(fileId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class FillRecord(val fileId: Int, val timestamp: Long, val length: Long, val flags: Int,
                     val nameId: Int, val parentId: Int, val overwriteAttrRef: Boolean, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_FILL_RECORD, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 4 + Long.SIZE_BYTES * 2 + 1 + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val timestamp = readLong()
            val length = readLong()
            val flags = readInt()
            val nameId = readInt()
            val parent = readInt()
            val overwriteAttrRef = readByte() == 1.toByte()
            val result = readResult<Unit>(enumerator)
            FillRecord(fileId, timestamp, length, flags, nameId, parent, overwriteAttrRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeLong(timestamp)
            writeLong(length)
            writeInt(flags)
            writeInt(nameId)
            writeInt(parentId)
            writeByte(if (overwriteAttrRef) { 1 } else { 0 })
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class CleanRecord(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_CLEAN_RECORD, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val result = readResult<Unit>(enumerator)
            CleanRecord(fileId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_VERSION, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val version = readInt()
            val result = readResult<Unit>(enumerator)
            RecordsOperation.SetVersion(version, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(version)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }
  }

  sealed class AttributesOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class WriteAttribute(val fileId: Int, val attributeIdEnumerated: Int, val attrDataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : AttributesOperation<Unit>(VfsOperationTag.ATTR_WRITE_ATTR, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>): WriteAttribute =
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

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
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

    class DeleteAttributes(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.ATTR_DELETE_ATTRS, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val fileId = readInt()
            val result = readResult<Unit>(enumerator)
            DeleteAttributes(fileId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.ATTR_SET_VERSION, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val version = readInt()
            val result = readResult<Unit>(enumerator)
            AttributesOperation.SetVersion(version, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(version)
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
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val fixedSize = readByte() == 1.toByte()
            val payloadRef = PayloadRef(readLong())
            val result = readResult<Unit>(enumerator)
            WriteBytes(recordId, fixedSize, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeByte(if (fixedSize) { 1 } else { 0 })
            writeLong(dataPayloadRef.value)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class WriteStream(val recordId: Int, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_STREAM, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val payloadRef = PayloadRef(readLong())
            val result = readResult<Unit>(enumerator)
            WriteStream(recordId, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeLong(dataPayloadRef.value)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class WriteStream2(val recordId: Int, val fixedSize: Boolean, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_STREAM_2, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val payloadRef = PayloadRef(readLong())
            val fixedSize = readByte() == 1.toByte()
            val result = readResult<Unit>(enumerator)
            WriteStream2(recordId, fixedSize, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeLong(dataPayloadRef.value)
            writeByte(if (fixedSize) { 1 } else { 0 })
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class AppendStream(val recordId: Int, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_APPEND_STREAM, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val payloadRef = PayloadRef(readLong())
            val result = readResult<Unit>(enumerator)
            AppendStream(recordId, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeLong(dataPayloadRef.value)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class ReplaceBytes(val recordId: Int, val offset: Int, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_REPLACE_BYTES, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES * 2 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val offset = readInt()
            val payloadRef = PayloadRef(readLong())
            val result = readResult<Unit>(enumerator)
            ReplaceBytes(recordId, offset, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeInt(offset)
            writeLong(dataPayloadRef.value)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class AcquireNewRecord(result: OperationResult<Int>)
      : ContentsOperation<Int>(VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD, result) {
      companion object {
        const val VALUE_SIZE_BYTES = OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val result = readResult<Int>(enumerator)
            AcquireNewRecord(result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class AcquireRecord(val recordId: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_ACQUIRE_RECORD, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            AcquireRecord(recordId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class ReleaseRecord(val recordId: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_RELEASE_RECORD, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            ReleaseRecord(recordId, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_SET_VERSION, result) {
      companion object {
        const val VALUE_SIZE_BYTES = Int.SIZE_BYTES + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val version = readInt()
            val result = readResult<Unit>(enumerator)
            ContentsOperation.SetVersion(version, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(version)
            writeInt(result.serialize(enumerator))
          }
          toByteArray()
        }
    }
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    fun <T : VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray, enumerator: DataEnumerator<String>): T =
      when (tag) {
        VfsOperationTag.NULL -> throw IllegalArgumentException("NULL descriptor is unexpected")

        VfsOperationTag.REC_ALLOC -> RecordsOperation.AllocateRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_ATTR_REC_ID -> RecordsOperation.SetAttributeRecordId.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_CONTENT_RECORD_ID -> RecordsOperation.SetContentRecordId.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_PARENT -> RecordsOperation.SetParent.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_NAME_ID -> RecordsOperation.SetNameId.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_FLAGS -> RecordsOperation.SetFlags.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_LENGTH -> RecordsOperation.SetLength.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_TIMESTAMP -> RecordsOperation.SetTimestamp.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED -> RecordsOperation.MarkRecordAsModified.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_FILL_RECORD -> RecordsOperation.FillRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_CLEAN_RECORD -> RecordsOperation.CleanRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.REC_SET_VERSION -> RecordsOperation.SetVersion.deserializeValue(data, enumerator) as T

        VfsOperationTag.ATTR_WRITE_ATTR -> AttributesOperation.WriteAttribute.deserializeValue(data, enumerator) as T
        VfsOperationTag.ATTR_DELETE_ATTRS -> AttributesOperation.DeleteAttributes.deserializeValue(data, enumerator) as T
        VfsOperationTag.ATTR_SET_VERSION -> AttributesOperation.SetVersion.deserializeValue(data, enumerator) as T

        VfsOperationTag.CONTENT_WRITE_BYTES -> ContentsOperation.WriteBytes.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_WRITE_STREAM -> ContentsOperation.WriteStream.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_WRITE_STREAM_2 -> ContentsOperation.WriteStream2.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_APPEND_STREAM -> ContentsOperation.AppendStream.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_REPLACE_BYTES -> ContentsOperation.ReplaceBytes.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD -> ContentsOperation.AcquireNewRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_ACQUIRE_RECORD -> ContentsOperation.AcquireRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_RELEASE_RECORD -> ContentsOperation.ReleaseRecord.deserializeValue(data, enumerator) as T
        VfsOperationTag.CONTENT_SET_VERSION -> ContentsOperation.SetVersion.deserializeValue(data, enumerator) as T
      }

    private inline fun <reified T : Any> DataInputStream.readResult(enumerator: DataEnumerator<String>) =
      OperationResult.deserialize<T>(readInt()) {
        enumerator.valueOf(it) ?: throw IllegalStateException("corrupted enumerator storage")
      }

    private inline fun <reified T : Any> OperationResult<T>.serialize(enumerator: DataEnumerator<String>): Int =
      serialize { enumerator.enumerate(it) }
  }
}

