// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.VirtualFile.PropName
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationResult.Companion.fromValue
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationResult.Companion.serialize
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Companion.readPayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Companion.writePayloadRef
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DataOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream


sealed class VfsOperation<T : Any>(val tag: VfsOperationTag, val result: OperationResult<T>) {
  // TODO: probably creating ByteArray for every serialization is overkill,
  //  maybe we should serialize into a provided DataOutputStream right away;
  //  same for deserialization
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            val overwriteAttrRef = readBoolean()
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
            writeBoolean(overwriteAttrRef)
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            WriteAttribute(fileId, attrIdEnumerated, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(fileId)
            writeInt(attributeIdEnumerated)
            writePayloadRef(attrDataPayloadRef)
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            val fixedSize = readBoolean()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            WriteBytes(recordId, fixedSize, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeBoolean(fixedSize)
            writePayloadRef(dataPayloadRef)
            writeResult(result, enumerator)
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
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            WriteStream(recordId, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writePayloadRef(dataPayloadRef)
            writeResult(result, enumerator)
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
            val payloadRef = readPayloadRef()
            val fixedSize = readBoolean()
            val result = readResult<Unit>(enumerator)
            WriteStream2(recordId, fixedSize, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writePayloadRef(dataPayloadRef)
            writeBoolean(fixedSize)
            writeResult(result, enumerator)
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
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            AppendStream(recordId, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writePayloadRef(dataPayloadRef)
            writeResult(result, enumerator)
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
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            ReplaceBytes(recordId, offset, payloadRef, result)
          }
      }

      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeInt(recordId)
            writeInt(offset)
            writePayloadRef(dataPayloadRef)
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
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
            writeResult(result, enumerator)
          }
          toByteArray()
        }
    }
  }

  sealed class VFileEventOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>)
    : VfsOperation<T>(tag, result) {
      sealed class EventStart(tag: VfsOperationTag, val eventTimestamp: Long)
        : VFileEventOperation<Unit>(tag, fromValue(Unit)){
        class ContentChange(eventTimestamp: Long, val fileId: Int)
          : EventStart(VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE, eventTimestamp) {
          override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
            ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
              DataOutputStream(this).run {
                writeLong(eventTimestamp)
                writeInt(fileId)
              }
              toByteArray()
            }

          companion object {
            const val VALUE_SIZE_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES

            fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
              DataInputStream(ByteArrayInputStream(data)).run {
                val eventTimestamp = readLong()
                val fileId = readInt()
                ContentChange(eventTimestamp, fileId)
              }
          }
        }

        class Copy(eventTimestamp: Long, val fileId: Int, val newParentId: Int)
          : EventStart(VfsOperationTag.VFILE_EVENT_COPY, eventTimestamp) {
          override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
            ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
              DataOutputStream(this).run {
                writeLong(eventTimestamp)
                writeInt(fileId)
                writeInt(newParentId)
              }
              toByteArray()
            }

          companion object {
            const val VALUE_SIZE_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES * 2

            fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
              DataInputStream(ByteArrayInputStream(data)).run {
                val eventTimestamp = readLong()
                val fileId = readInt()
                val newParentId = readInt()
                Copy(eventTimestamp, fileId, newParentId)
              }
          }
        }

        class Create(
          eventTimestamp: Long,
          val parentId: Int,
          val newChildName: PayloadRef, // TODO: is it really needed?
          val isDirectory: Boolean
        ) : EventStart(VfsOperationTag.VFILE_EVENT_CREATE, eventTimestamp) {
          override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
            ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
              DataOutputStream(this).run {
                writeLong(eventTimestamp)
                writeInt(parentId)
                writePayloadRef(newChildName)
                writeBoolean(isDirectory)
              }
              toByteArray()
            }

          companion object {
            const val VALUE_SIZE_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + 1

            fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
              DataInputStream(ByteArrayInputStream(data)).run {
                val eventTimestamp = readLong()
                val parentId = readInt()
                val newChildName = readPayloadRef()
                val isDirectory = readBoolean()
                Create(eventTimestamp, parentId, newChildName, isDirectory)
              }
          }
        }

        class Delete(eventTimestamp: Long, val fileId: Int) : EventStart(VfsOperationTag.VFILE_EVENT_DELETE, eventTimestamp) {
          override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
            ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
              DataOutputStream(this).run {
                writeLong(eventTimestamp)
                writeInt(fileId)
              }
              toByteArray()
            }

          companion object {
            const val VALUE_SIZE_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES

            fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
              DataInputStream(ByteArrayInputStream(data)).run {
                val eventTimestamp = readLong()
                val fileId = readInt()
                Delete(eventTimestamp, fileId)
              }
          }
        }

        class Move(eventTimestamp: Long, val fileId: Int, val oldParentId: Int, val newParentId: Int)
          : EventStart(VfsOperationTag.VFILE_EVENT_MOVE, eventTimestamp) {
          override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
            ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
              DataOutputStream(this).run {
                writeLong(eventTimestamp)
                writeInt(fileId)
                writeInt(oldParentId)
                writeInt(newParentId)
              }
              toByteArray()
            }

          companion object {
            const val VALUE_SIZE_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES * 3

            fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
              DataInputStream(ByteArrayInputStream(data)).run {
                val eventTimestamp = readLong()
                val fileId = readInt()
                val oldParentId = readInt()
                val newParentId = readInt()
                Move(eventTimestamp, fileId, oldParentId, newParentId)
              }
          }
        }

        class PropertyChange(eventTimestamp: Long, val fileId: Int, @PropName val propertyName: String)
          : EventStart(VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED, eventTimestamp) {
          override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
            ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
              DataOutputStream(this).run {
                writeLong(eventTimestamp)
                writeInt(fileId)
                writeInt(enumerator.enumerate(propertyName))
              }
              toByteArray()
            }

          companion object {
            const val VALUE_SIZE_BYTES = Long.SIZE_BYTES + Int.SIZE_BYTES * 2

            fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
              DataInputStream(ByteArrayInputStream(data)).run {
                val eventTimestamp = readLong()
                val fileId = readInt()
                val propertyName = enumerator.valueOf(readInt())!!
                PropertyChange(eventTimestamp, fileId, propertyName)
              }
          }
        }
      }

    class EventEnd(val eventTag: VfsOperationTag, result: OperationResult<Unit>)
      : VFileEventOperation<Unit>(VfsOperationTag.VFILE_EVENT_END, result) {
      override fun serializeValue(enumerator: DataEnumerator<String>): ByteArray =
        ByteArrayOutputStream(VALUE_SIZE_BYTES).run {
          DataOutputStream(this).run {
            writeByte(eventTag.ordinal)
            writeResult(result, enumerator)
          }
          toByteArray()
        }

      companion object {
        const val VALUE_SIZE_BYTES = 1 + OperationResult.SIZE_BYTES

        fun deserializeValue(data: ByteArray, enumerator: DataEnumerator<String>) =
          DataInputStream(ByteArrayInputStream(data)).run {
            val tag = readByte()
            val result = readResult<Unit>(enumerator)
            EventEnd(VfsOperationTag.values()[tag.toInt()].also {
              if (!it.isVFileEventOperation) {
                throw IllegalStateException("unexpected EventEnd tag: $it")
              }
            }, result)
          }
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

        VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE -> VFileEventOperation.EventStart.ContentChange.deserializeValue(data, enumerator) as T
        VfsOperationTag.VFILE_EVENT_COPY -> VFileEventOperation.EventStart.Copy.deserializeValue(data, enumerator) as T
        VfsOperationTag.VFILE_EVENT_CREATE -> VFileEventOperation.EventStart.Create.deserializeValue(data, enumerator) as T
        VfsOperationTag.VFILE_EVENT_DELETE -> VFileEventOperation.EventStart.Delete.deserializeValue(data, enumerator) as T
        VfsOperationTag.VFILE_EVENT_MOVE -> VFileEventOperation.EventStart.Move.deserializeValue(data, enumerator) as T
        VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED -> VFileEventOperation.EventStart.PropertyChange.deserializeValue(data, enumerator) as T
        VfsOperationTag.VFILE_EVENT_END -> VFileEventOperation.EventEnd.deserializeValue(data, enumerator) as T
      }

    private inline fun <reified T : Any> DataInputStream.readResult(enumerator: DataEnumerator<String>) =
      OperationResult.deserialize<T>(readInt()) {
        enumerator.valueOf(it) ?: throw IllegalStateException("corrupted enumerator storage")
      }

    private inline fun <reified T : Any> DataOutputStream.writeResult(result: OperationResult<T>, enumerator: DataEnumerator<String>) =
      writeInt(result.serialize { enumerator.enumerate(it) })
  }
}

