// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.VirtualFile.PropName
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationResult.Companion.fromValue
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationResult.Companion.serialize
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Companion.readPayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Companion.writePayloadRef
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.UnsyncByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream


sealed class VfsOperation<T : Any>(val tag: VfsOperationTag, val result: OperationResult<T>) {
  interface Serializer<O : VfsOperation<*>> {
    val valueSizeBytes: Int
    fun OutputStream.serialize(operation: O, enumerator: DataEnumerator<String>)
    fun InputStream.deserialize(enumerator: DataEnumerator<String>): O
  }

  val serializer get() = tag.operationSerializer

  sealed class RecordsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class AllocateRecord(result: OperationResult<Int>) : RecordsOperation<Int>(VfsOperationTag.REC_ALLOC, result) {
      internal companion object : Serializer<AllocateRecord> {
        override val valueSizeBytes: Int = OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AllocateRecord =
          DataInputStream(this).run {
            val result = readResult<Int>(enumerator)
            return AllocateRecord(result)
          }

        override fun OutputStream.serialize(operation: AllocateRecord, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetAttributeRecordId(val fileId: Int, val recordId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_ATTR_REC_ID, result) {
      internal companion object : Serializer<SetAttributeRecordId> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetAttributeRecordId =
          DataInputStream(this).run {
            val fileId = readInt()
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            return SetAttributeRecordId(fileId, recordId, result)
          }

        override fun OutputStream.serialize(operation: SetAttributeRecordId, enumerator: DataEnumerator<String>) =
          DataOutputStream(this).run {
            writeInt(operation.fileId)
            writeInt(operation.recordId)
            writeResult(operation.result, enumerator)
          }
      }
    }

    class SetContentRecordId(val fileId: Int, val recordId: Int, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_CONTENT_RECORD_ID, result) {
      internal companion object : Serializer<SetContentRecordId> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetContentRecordId =
          DataInputStream(this).run {
            val fileId = readInt()
            val recordId = readInt()
            val result = readResult<Boolean>(enumerator)
            return SetContentRecordId(fileId, recordId, result)
          }

        override fun OutputStream.serialize(operation: SetContentRecordId, enumerator: DataEnumerator<String>) =
          DataOutputStream(this).run {
            writeInt(operation.fileId)
            writeInt(operation.recordId)
            writeResult(operation.result, enumerator)
          }
      }
    }

    class SetParent(val fileId: Int, val parentId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_PARENT, result) {
      internal companion object : Serializer<SetParent> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetParent =
          DataInputStream(this).run {
            val fileId = readInt()
            val parentId = readInt()
            val result = readResult<Unit>(enumerator)
            return SetParent(fileId, parentId, result)
          }

        override fun OutputStream.serialize(operation: SetParent, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.parentId)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetNameId(val fileId: Int, val nameId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_NAME_ID, result) {
      internal companion object : Serializer<SetNameId> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetNameId =
          DataInputStream(this).run {
            val fileId = readInt()
            val nameId = readInt()
            val result = readResult<Unit>(enumerator)
            return SetNameId(fileId, nameId, result)
          }

        override fun OutputStream.serialize(operation: SetNameId, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.nameId)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetFlags(val fileId: Int, val flags: Int, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_FLAGS, result) {
      internal companion object : Serializer<SetFlags> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetFlags =
          DataInputStream(this).run {
            val fileId = readInt()
            val flags = readInt()
            val result = readResult<Boolean>(enumerator)
            return SetFlags(fileId, flags, result)
          }

        override fun OutputStream.serialize(operation: SetFlags, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.flags)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetLength(val fileId: Int, val length: Long, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_LENGTH, result) {
      internal companion object : Serializer<SetLength> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + Long.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetLength =
          DataInputStream(this).run {
            val fileId = readInt()
            val length = readLong()
            val result = readResult<Boolean>(enumerator)
            return SetLength(fileId, length, result)
          }

        override fun OutputStream.serialize(operation: SetLength, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.length)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetTimestamp(val fileId: Int, val timestamp: Long, result: OperationResult<Boolean>)
      : RecordsOperation<Boolean>(VfsOperationTag.REC_SET_TIMESTAMP, result) {
      internal companion object : Serializer<SetTimestamp> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + Long.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetTimestamp =
          DataInputStream(this).run {
            val fileId = readInt()
            val timestamp = readLong()
            val result = readResult<Boolean>(enumerator)
            return SetTimestamp(fileId, timestamp, result)
          }

        override fun OutputStream.serialize(operation: SetTimestamp, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.timestamp)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class MarkRecordAsModified(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED, result) {
      internal companion object : Serializer<MarkRecordAsModified> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): MarkRecordAsModified =
          DataInputStream(this).run {
            val fileId = readInt()
            val result = readResult<Unit>(enumerator)
            return MarkRecordAsModified(fileId, result)
          }

        override fun OutputStream.serialize(operation: MarkRecordAsModified, enumerator: DataEnumerator<String>) =
          DataOutputStream(this).run {
            writeInt(operation.fileId)
            writeResult(operation.result, enumerator)
          }
      }
    }

    class FillRecord(val fileId: Int, val timestamp: Long, val length: Long, val flags: Int,
                     val nameId: Int, val parentId: Int, val overwriteAttrRef: Boolean, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_FILL_RECORD, result) {
      internal companion object : Serializer<FillRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 4 + Long.SIZE_BYTES * 2 + 1 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): FillRecord =
          DataInputStream(this).run {
            val fileId = readInt()
            val timestamp = readLong()
            val length = readLong()
            val flags = readInt()
            val nameId = readInt()
            val parent = readInt()
            val overwriteAttrRef = readBoolean()
            val result = readResult<Unit>(enumerator)
            return FillRecord(fileId, timestamp, length, flags, nameId, parent, overwriteAttrRef, result)
          }

        override fun OutputStream.serialize(operation: FillRecord, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.timestamp)
          writeLong(operation.length)
          writeInt(operation.flags)
          writeInt(operation.nameId)
          writeInt(operation.parentId)
          writeBoolean(operation.overwriteAttrRef)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class CleanRecord(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_CLEAN_RECORD, result) {
      internal companion object : Serializer<CleanRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): CleanRecord =
          DataInputStream(this).run {
            val fileId = readInt()
            val result = readResult<Unit>(enumerator)
            return CleanRecord(fileId, result)
          }

        override fun OutputStream.serialize(operation: CleanRecord, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_VERSION, result) {
      internal companion object : Serializer<SetVersion> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetVersion =
          DataInputStream(this).run {
            val version = readInt()
            val result = readResult<Unit>(enumerator)
            return RecordsOperation.SetVersion(version, result)
          }

        override fun OutputStream.serialize(operation: SetVersion, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.version)
          writeResult(operation.result, enumerator)
        }
      }
    }
  }

  sealed class AttributesOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    // TODO: maybe attribute version should also saved
    class WriteAttribute(val fileId: Int, val attributeIdEnumerated: Int, val attrDataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : AttributesOperation<Unit>(VfsOperationTag.ATTR_WRITE_ATTR, result) {
      internal companion object : Serializer<WriteAttribute> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteAttribute =
          DataInputStream(this).run {
            val fileId = readInt()
            val attrIdEnumerated = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            return WriteAttribute(fileId, attrIdEnumerated, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteAttribute, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.attributeIdEnumerated)
          writePayloadRef(operation.attrDataPayloadRef)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class DeleteAttributes(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.ATTR_DELETE_ATTRS, result) {
      internal companion object : Serializer<DeleteAttributes> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): DeleteAttributes =
          DataInputStream(this).run {
            val fileId = readInt()
            val result = readResult<Unit>(enumerator)
            return DeleteAttributes(fileId, result)
          }

        override fun OutputStream.serialize(operation: DeleteAttributes, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.ATTR_SET_VERSION, result) {
      internal companion object : Serializer<AttributesOperation.SetVersion> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AttributesOperation.SetVersion =
          DataInputStream(this).run {
            val version = readInt()
            val result = readResult<Unit>(enumerator)
            return AttributesOperation.SetVersion(version, result)
          }

        override fun OutputStream.serialize(operation: AttributesOperation.SetVersion,
                                            enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.version)
          writeResult(operation.result, enumerator)
        }
      }
    }
  }

  sealed class ContentsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class WriteBytes(val recordId: Int, val fixedSize: Boolean, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_BYTES, result) {
      internal companion object : Serializer<WriteBytes> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteBytes =
          DataInputStream(this).run {
            val recordId = readInt()
            val fixedSize = readBoolean()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            return WriteBytes(recordId, fixedSize, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteBytes, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeBoolean(operation.fixedSize)
          writePayloadRef(operation.dataPayloadRef)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class WriteStream(val recordId: Int, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_STREAM, result) {
      internal companion object : Serializer<WriteStream> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteStream =
          DataInputStream(this).run {
            val recordId = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            return WriteStream(recordId, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteStream, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writePayloadRef(operation.dataPayloadRef)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class WriteStream2(val recordId: Int, val fixedSize: Boolean, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_STREAM_2, result) {
      internal companion object : Serializer<WriteStream2> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteStream2 =
          DataInputStream(this).run {
            val recordId = readInt()
            val payloadRef = readPayloadRef()
            val fixedSize = readBoolean()
            val result = readResult<Unit>(enumerator)
            return WriteStream2(recordId, fixedSize, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteStream2, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writePayloadRef(operation.dataPayloadRef)
          writeBoolean(operation.fixedSize)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class AppendStream(val recordId: Int, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_APPEND_STREAM, result) {
      internal companion object : Serializer<AppendStream> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AppendStream =
          DataInputStream(this).run {
            val recordId = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            return AppendStream(recordId, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: AppendStream, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writePayloadRef(operation.dataPayloadRef)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class ReplaceBytes(val recordId: Int, val offset: Int, val dataPayloadRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_REPLACE_BYTES, result) {
      internal companion object : Serializer<ReplaceBytes> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ReplaceBytes =
          DataInputStream(this).run {
            val recordId = readInt()
            val offset = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>(enumerator)
            return ReplaceBytes(recordId, offset, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: ReplaceBytes, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeInt(operation.offset)
          writePayloadRef(operation.dataPayloadRef)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class AcquireNewRecord(result: OperationResult<Int>)
      : ContentsOperation<Int>(VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD, result) {
      internal companion object : Serializer<AcquireNewRecord> {
        override val valueSizeBytes: Int = OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AcquireNewRecord =
          DataInputStream(this).run {
            val result = readResult<Int>(enumerator)
            return AcquireNewRecord(result)
          }

        override fun OutputStream.serialize(operation: AcquireNewRecord, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeResult(operation.result, enumerator)
        }
      }
    }

    class AcquireRecord(val recordId: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_ACQUIRE_RECORD, result) {
      internal companion object : Serializer<AcquireRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AcquireRecord =
          DataInputStream(this).run {
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            return AcquireRecord(recordId, result)
          }

        override fun OutputStream.serialize(operation: AcquireRecord, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class ReleaseRecord(val recordId: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_RELEASE_RECORD, result) {
      internal companion object : Serializer<ReleaseRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ReleaseRecord =
          DataInputStream(this).run {
            val recordId = readInt()
            val result = readResult<Unit>(enumerator)
            return ReleaseRecord(recordId, result)
          }

        override fun OutputStream.serialize(operation: ReleaseRecord, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeResult(operation.result, enumerator)
        }
      }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_SET_VERSION, result) {
      internal companion object : Serializer<ContentsOperation.SetVersion> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ContentsOperation.SetVersion =
          DataInputStream(this).run {
            val version = readInt()
            val result = readResult<Unit>(enumerator)
            return ContentsOperation.SetVersion(version, result)
          }

        override fun OutputStream.serialize(operation: ContentsOperation.SetVersion, enumerator: DataEnumerator<String>) =
          DataOutputStream(this).run {
            writeInt(operation.version)
            writeResult(operation.result, enumerator)
          }
      }
    }
  }

  sealed class VFileEventOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>)
    : VfsOperation<T>(tag, result) {
    sealed class EventStart(tag: VfsOperationTag, val eventTimestamp: Long)
      : VFileEventOperation<Unit>(tag, fromValue(Unit)) {
      class ContentChange(eventTimestamp: Long, val fileId: Int)
        : EventStart(VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE, eventTimestamp) {
        internal companion object : Serializer<ContentChange> {
          override val valueSizeBytes: Int = Long.SIZE_BYTES + Int.SIZE_BYTES
          override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ContentChange =
            DataInputStream(this).run {
              val eventTimestamp = readLong()
              val fileId = readInt()
              return ContentChange(eventTimestamp, fileId)
            }

          override fun OutputStream.serialize(operation: ContentChange, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
          }
        }
      }

      class Copy(eventTimestamp: Long, val fileId: Int, val newParentId: Int)
        : EventStart(VfsOperationTag.VFILE_EVENT_COPY, eventTimestamp) {
        internal companion object : Serializer<Copy> {
          override val valueSizeBytes: Int = Long.SIZE_BYTES + Int.SIZE_BYTES * 2
          override fun InputStream.deserialize(enumerator: DataEnumerator<String>): Copy =
            DataInputStream(this).run {
              val eventTimestamp = readLong()
              val fileId = readInt()
              val newParentId = readInt()
              return Copy(eventTimestamp, fileId, newParentId)
            }

          override fun OutputStream.serialize(operation: Copy, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
            writeInt(operation.newParentId)
          }
        }
      }

      class Create(
        eventTimestamp: Long,
        val parentId: Int,
        val newChildName: PayloadRef, // TODO: is it really needed?
        val isDirectory: Boolean
      ) : EventStart(VfsOperationTag.VFILE_EVENT_CREATE, eventTimestamp) {
        internal companion object : Serializer<Create> {
          override val valueSizeBytes: Int = Long.SIZE_BYTES + Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + 1
          override fun InputStream.deserialize(enumerator: DataEnumerator<String>): Create =
            DataInputStream(this).run {
              val eventTimestamp = readLong()
              val parentId = readInt()
              val newChildName = readPayloadRef()
              val isDirectory = readBoolean()
              return Create(eventTimestamp, parentId, newChildName, isDirectory)
            }

          override fun OutputStream.serialize(operation: Create, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.parentId)
            writePayloadRef(operation.newChildName)
            writeBoolean(operation.isDirectory)
          }
        }
      }

      class Delete(eventTimestamp: Long, val fileId: Int) : EventStart(VfsOperationTag.VFILE_EVENT_DELETE, eventTimestamp) {
        internal companion object : Serializer<Delete> {
          override val valueSizeBytes: Int = Long.SIZE_BYTES + Int.SIZE_BYTES
          override fun InputStream.deserialize(enumerator: DataEnumerator<String>): Delete =
            DataInputStream(this).run {
              val eventTimestamp = readLong()
              val fileId = readInt()
              return Delete(eventTimestamp, fileId)
            }

          override fun OutputStream.serialize(operation: Delete, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
          }
        }
      }

      class Move(eventTimestamp: Long, val fileId: Int, val oldParentId: Int, val newParentId: Int)
        : EventStart(VfsOperationTag.VFILE_EVENT_MOVE, eventTimestamp) {
        internal companion object : Serializer<Move> {
          override val valueSizeBytes: Int = Long.SIZE_BYTES + Int.SIZE_BYTES * 3
          override fun InputStream.deserialize(enumerator: DataEnumerator<String>): Move =
            DataInputStream(this).run {
              val eventTimestamp = readLong()
              val fileId = readInt()
              val oldParentId = readInt()
              val newParentId = readInt()
              return Move(eventTimestamp, fileId, oldParentId, newParentId)
            }

          override fun OutputStream.serialize(operation: Move, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
            writeInt(operation.oldParentId)
            writeInt(operation.newParentId)
          }
        }
      }

      class PropertyChange(eventTimestamp: Long, val fileId: Int, @PropName val propertyName: String)
        : EventStart(VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED, eventTimestamp) {
        internal companion object : Serializer<PropertyChange> {
          override val valueSizeBytes: Int = Long.SIZE_BYTES + Int.SIZE_BYTES * 2
          override fun InputStream.deserialize(enumerator: DataEnumerator<String>): PropertyChange =
            DataInputStream(this).run {
              val eventTimestamp = readLong()
              val fileId = readInt()
              val propertyName = enumerator.valueOf(readInt())!!
              return PropertyChange(eventTimestamp, fileId, propertyName)
            }

          override fun OutputStream.serialize(operation: PropertyChange, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
            writeInt(enumerator.enumerate(operation.propertyName))
          }
        }
      }
    }

    class EventEnd(val eventTag: VfsOperationTag, result: OperationResult<Unit>)
      : VFileEventOperation<Unit>(VfsOperationTag.VFILE_EVENT_END, result) {
      internal companion object : Serializer<EventEnd> {
        override val valueSizeBytes: Int = 1 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): EventEnd =
          DataInputStream(this).run {
            val tag = readByte()
            val result = readResult<Unit>(enumerator)
            return EventEnd(VfsOperationTag.values()[tag.toInt()].also {
              if (!it.isVFileEventOperation) {
                throw IllegalStateException("unexpected EventEnd tag: $it")
              }
            }, result)
          }

        override fun OutputStream.serialize(operation: EventEnd, enumerator: DataEnumerator<String>) = DataOutputStream(this).run {
          writeByte(operation.eventTag.ordinal)
          writeResult(operation.result, enumerator)
        }
      }
    }
  }

  companion object {
    private inline fun <reified T : Any> DataInputStream.readResult(enumerator: DataEnumerator<String>) =
      OperationResult.deserialize<T>(readInt()) {
        enumerator.valueOf(it) ?: throw IllegalStateException("corrupted enumerator storage")
      }

    private inline fun <reified T : Any> DataOutputStream.writeResult(result: OperationResult<T>, enumerator: DataEnumerator<String>) =
      writeInt(result.serialize { enumerator.enumerate(it) })
  }
}

@Suppress("UNCHECKED_CAST")
fun <O : VfsOperation<*>> VfsOperation.Serializer<O>.serializeOperation(operation: VfsOperation<*>,
                                                                        enumerator: DataEnumerator<String>,
                                                                        out: OutputStream) =
  out.serialize(operation as O, enumerator)

@Suppress("UNCHECKED_CAST")
fun <O : VfsOperation<*>> VfsOperation.Serializer<O>.serializeOperation(operation: VfsOperation<*>,
                                                                        enumerator: DataEnumerator<String>): ByteArray {
  val out = ByteArrayOutputStream(valueSizeBytes)
  out.serialize(operation as O, enumerator)
  return out.toByteArray()
}

fun <O : VfsOperation<*>> VfsOperation.Serializer<O>.deserializeOperation(data: ByteArray, enumerator: DataEnumerator<String>): O {
  return deserializeOperation(data, 0, data.size, enumerator)
}

fun <O : VfsOperation<*>> VfsOperation.Serializer<O>.deserializeOperation(data: ByteArray,
                                                                          offset: Int,
                                                                          length: Int,
                                                                          enumerator: DataEnumerator<String>): O {
  assert(length == valueSizeBytes)
  return UnsyncByteArrayInputStream(data, offset, length).deserialize(enumerator)
}