// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  val serializer: Serializer<*> get() = tag.operationSerializer

  interface PayloadContainingOperation {
    val dataRef: PayloadRef
  }

  sealed class RecordsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class AllocateRecord(result: OperationResult<Int>) : RecordsOperation<Int>(VfsOperationTag.REC_ALLOC, result) {
      internal companion object : Serializer<AllocateRecord> {
        override val valueSizeBytes: Int = OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AllocateRecord =
          DataInputStream(this).run {
            val result = readResult<Int>()
            return AllocateRecord(result)
          }

        override fun OutputStream.serialize(operation: AllocateRecord, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "AllocateRecord(result=$result)"
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
            val result = readResult<Unit>()
            return SetAttributeRecordId(fileId, recordId, result)
          }

        override fun OutputStream.serialize(operation: SetAttributeRecordId, enumerator: DataEnumerator<String>): Unit =
          DataOutputStream(this).run {
            writeInt(operation.fileId)
            writeInt(operation.recordId)
            writeResult(operation.result)
          }
      }

      override fun toString(): String {
        return "SetAttributeRecordId(fileId=$fileId, recordId=$recordId, result=$result)"
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
            val result = readResult<Boolean>()
            return SetContentRecordId(fileId, recordId, result)
          }

        override fun OutputStream.serialize(operation: SetContentRecordId, enumerator: DataEnumerator<String>): Unit =
          DataOutputStream(this).run {
            writeInt(operation.fileId)
            writeInt(operation.recordId)
            writeResult(operation.result)
          }
      }

      override fun toString(): String {
        return "SetContentRecordId(fileId=$fileId, recordId=$recordId, result=$result)"
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
            val result = readResult<Unit>()
            return SetParent(fileId, parentId, result)
          }

        override fun OutputStream.serialize(operation: SetParent, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.parentId)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetParent(fileId=$fileId, parentId=$parentId, result=$result)"
      }
    }

    class SetNameId(val fileId: Int, val nameId: Int, result: OperationResult<Int>)
      : RecordsOperation<Int>(VfsOperationTag.REC_SET_NAME_ID, result) {
      internal companion object : Serializer<SetNameId> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetNameId =
          DataInputStream(this).run {
            val fileId = readInt()
            val nameId = readInt()
            val result = readResult<Int>()
            return SetNameId(fileId, nameId, result)
          }

        override fun OutputStream.serialize(operation: SetNameId, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.nameId)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetNameId(fileId=$fileId, nameId=$nameId, result=$result)"
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
            val result = readResult<Boolean>()
            return SetFlags(fileId, flags, result)
          }

        override fun OutputStream.serialize(operation: SetFlags, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeInt(operation.flags)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetFlags(fileId=$fileId, flags=$flags, result=$result)"
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
            val result = readResult<Boolean>()
            return SetLength(fileId, length, result)
          }

        override fun OutputStream.serialize(operation: SetLength, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.length)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetLength(fileId=$fileId, length=$length, result=$result)"
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
            val result = readResult<Boolean>()
            return SetTimestamp(fileId, timestamp, result)
          }

        override fun OutputStream.serialize(operation: SetTimestamp, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.timestamp)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetTimestamp(fileId=$fileId, timestamp=$timestamp, result=$result)"
      }
    }

    class MarkRecordAsModified(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED, result) {
      internal companion object : Serializer<MarkRecordAsModified> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): MarkRecordAsModified =
          DataInputStream(this).run {
            val fileId = readInt()
            val result = readResult<Unit>()
            return MarkRecordAsModified(fileId, result)
          }

        override fun OutputStream.serialize(operation: MarkRecordAsModified, enumerator: DataEnumerator<String>): Unit =
          DataOutputStream(this).run {
            writeInt(operation.fileId)
            writeResult(operation.result)
          }
      }

      override fun toString(): String {
        return "MarkRecordAsModified(fileId=$fileId, result=$result)"
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
            val result = readResult<Unit>()
            return FillRecord(fileId, timestamp, length, flags, nameId, parent, overwriteAttrRef, result)
          }

        override fun OutputStream.serialize(operation: FillRecord, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.timestamp)
          writeLong(operation.length)
          writeInt(operation.flags)
          writeInt(operation.nameId)
          writeInt(operation.parentId)
          writeBoolean(operation.overwriteAttrRef)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "FillRecord(fileId=$fileId, timestamp=$timestamp, length=$length, flags=$flags, nameId=$nameId, " +
               "parentId=$parentId, overwriteAttrRef=$overwriteAttrRef, result=$result)"
      }
    }

    class CleanRecord(val fileId: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_CLEAN_RECORD, result) {
      internal companion object : Serializer<CleanRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): CleanRecord =
          DataInputStream(this).run {
            val fileId = readInt()
            val result = readResult<Unit>()
            return CleanRecord(fileId, result)
          }

        override fun OutputStream.serialize(operation: CleanRecord, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "CleanRecord(fileId=$fileId, result=$result)"
      }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : RecordsOperation<Unit>(VfsOperationTag.REC_SET_VERSION, result) {
      internal companion object : Serializer<SetVersion> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): SetVersion =
          DataInputStream(this).run {
            val version = readInt()
            val result = readResult<Unit>()
            return RecordsOperation.SetVersion(version, result)
          }

        override fun OutputStream.serialize(operation: SetVersion, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.version)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetVersion(version=$version, result=$result)"
      }
    }

    companion object {
      val <T: Any> RecordsOperation<T>.fileId: Int? get() = when (this) {
        is AllocateRecord -> if (result.isSuccess) result.value else null
        is CleanRecord -> fileId
        is FillRecord -> fileId
        is MarkRecordAsModified -> fileId
        is SetAttributeRecordId -> fileId
        is SetContentRecordId -> fileId
        is SetFlags -> fileId
        is SetLength -> fileId
        is SetNameId -> fileId
        is SetParent -> fileId
        is SetTimestamp -> fileId
        is SetVersion -> null
      }
    }
  }

  sealed class AttributesOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class WriteAttribute(val fileId: Int, val enumeratedAttribute: EnumeratedFileAttribute, override val dataRef: PayloadRef, result: OperationResult<Unit>)
      : AttributesOperation<Unit>(VfsOperationTag.ATTR_WRITE_ATTR, result), PayloadContainingOperation {
      internal companion object : Serializer<WriteAttribute> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + EnumeratedFileAttribute.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteAttribute =
          DataInputStream(this).run {
            val fileId = readInt()
            val attrIdEnumerated = readLong().toULong()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>()
            return WriteAttribute(fileId, EnumeratedFileAttribute(attrIdEnumerated), payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteAttribute, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeLong(operation.enumeratedAttribute.compressedInfo.toLong())
          writePayloadRef(operation.dataRef)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "WriteAttribute(fileId=$fileId, enumeratedAttribute=$enumeratedAttribute, dataRef=$dataRef, result=$result)"
      }
    }

    class DeleteAttributes(val fileId: Int, result: OperationResult<Unit>)
      : AttributesOperation<Unit>(VfsOperationTag.ATTR_DELETE_ATTRS, result) {
      internal companion object : Serializer<DeleteAttributes> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): DeleteAttributes =
          DataInputStream(this).run {
            val fileId = readInt()
            val result = readResult<Unit>()
            return DeleteAttributes(fileId, result)
          }

        override fun OutputStream.serialize(operation: DeleteAttributes, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.fileId)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "DeleteAttributes(fileId=$fileId, result=$result)"
      }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : AttributesOperation<Unit>(VfsOperationTag.ATTR_SET_VERSION, result) {
      internal companion object : Serializer<AttributesOperation.SetVersion> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AttributesOperation.SetVersion =
          DataInputStream(this).run {
            val version = readInt()
            val result = readResult<Unit>()
            return AttributesOperation.SetVersion(version, result)
          }

        override fun OutputStream.serialize(operation: AttributesOperation.SetVersion,
                                            enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.version)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "SetVersion(version=$version, result=$result)"
      }
    }

    companion object {
      val AttributesOperation<*>.fileId: Int? get() = when (this) {
        is WriteAttribute -> fileId
        is DeleteAttributes -> fileId
        is SetVersion -> null
      }
    }
  }

  sealed class ContentsOperation<T : Any>(tag: VfsOperationTag, result: OperationResult<T>) : VfsOperation<T>(tag, result) {
    class WriteBytes(val recordId: Int, val fixedSize: Boolean, override val dataRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_BYTES, result), PayloadContainingOperation {

      internal companion object : Serializer<WriteBytes> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteBytes =
          DataInputStream(this).run {
            val recordId = readInt()
            val fixedSize = readBoolean()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>()
            return WriteBytes(recordId, fixedSize, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteBytes, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeBoolean(operation.fixedSize)
          writePayloadRef(operation.dataRef)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "WriteBytes(recordId=$recordId, fixedSize=$fixedSize, dataRef=$dataRef, result=$result)"
      }
    }

    class WriteStream(val recordId: Int, override val dataRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_STREAM, result), PayloadContainingOperation {
      internal companion object : Serializer<WriteStream> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteStream =
          DataInputStream(this).run {
            val recordId = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>()
            return WriteStream(recordId, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteStream, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writePayloadRef(operation.dataRef)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "WriteStream(recordId=$recordId, dataRef=$dataRef, result=$result)"
      }
    }

    class WriteStream2(val recordId: Int, val fixedSize: Boolean, override val dataRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_WRITE_STREAM_2, result), PayloadContainingOperation {
      internal companion object : Serializer<WriteStream2> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + 1 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): WriteStream2 =
          DataInputStream(this).run {
            val recordId = readInt()
            val payloadRef = readPayloadRef()
            val fixedSize = readBoolean()
            val result = readResult<Unit>()
            return WriteStream2(recordId, fixedSize, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: WriteStream2, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writePayloadRef(operation.dataRef)
          writeBoolean(operation.fixedSize)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "WriteStream2(recordId=$recordId, fixedSize=$fixedSize, dataRef=$dataRef, result=$result)"
      }
    }

    class AppendStream(val recordId: Int, override val dataRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_APPEND_STREAM, result), PayloadContainingOperation {
      internal companion object : Serializer<AppendStream> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AppendStream =
          DataInputStream(this).run {
            val recordId = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>()
            return AppendStream(recordId, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: AppendStream, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writePayloadRef(operation.dataRef)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "AppendStream(recordId=$recordId, dataRef=$dataRef, result=$result)"
      }
    }

    class ReplaceBytes(val recordId: Int, val offset: Int, override val dataRef: PayloadRef, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_REPLACE_BYTES, result), PayloadContainingOperation {
      internal companion object : Serializer<ReplaceBytes> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES * 2 + PayloadRef.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ReplaceBytes =
          DataInputStream(this).run {
            val recordId = readInt()
            val offset = readInt()
            val payloadRef = readPayloadRef()
            val result = readResult<Unit>()
            return ReplaceBytes(recordId, offset, payloadRef, result)
          }

        override fun OutputStream.serialize(operation: ReplaceBytes, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeInt(operation.offset)
          writePayloadRef(operation.dataRef)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "ReplaceBytes(recordId=$recordId, offset=$offset, dataRef=$dataRef, result=$result)"
      }
    }

    class AcquireNewRecord(result: OperationResult<Int>)
      : ContentsOperation<Int>(VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD, result) {
      internal companion object : Serializer<AcquireNewRecord> {
        override val valueSizeBytes: Int = OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AcquireNewRecord =
          DataInputStream(this).run {
            val result = readResult<Int>()
            return AcquireNewRecord(result)
          }

        override fun OutputStream.serialize(operation: AcquireNewRecord, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "AcquireNewRecord(result=$result)"
      }
    }

    class AcquireRecord(val recordId: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_ACQUIRE_RECORD, result) {
      internal companion object : Serializer<AcquireRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): AcquireRecord =
          DataInputStream(this).run {
            val recordId = readInt()
            val result = readResult<Unit>()
            return AcquireRecord(recordId, result)
          }

        override fun OutputStream.serialize(operation: AcquireRecord, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "AcquireRecord(recordId=$recordId, result=$result)"
      }

    }

    class ReleaseRecord(val recordId: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_RELEASE_RECORD, result) {
      internal companion object : Serializer<ReleaseRecord> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ReleaseRecord =
          DataInputStream(this).run {
            val recordId = readInt()
            val result = readResult<Unit>()
            return ReleaseRecord(recordId, result)
          }

        override fun OutputStream.serialize(operation: ReleaseRecord, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeInt(operation.recordId)
          writeResult(operation.result)
        }
      }

      override fun toString(): String {
        return "ReleaseRecord(recordId=$recordId, result=$result)"
      }
    }

    class SetVersion(val version: Int, result: OperationResult<Unit>)
      : ContentsOperation<Unit>(VfsOperationTag.CONTENT_SET_VERSION, result) {
      internal companion object : Serializer<ContentsOperation.SetVersion> {
        override val valueSizeBytes: Int = Int.SIZE_BYTES + OperationResult.SIZE_BYTES
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): ContentsOperation.SetVersion =
          DataInputStream(this).run {
            val version = readInt()
            val result = readResult<Unit>()
            return ContentsOperation.SetVersion(version, result)
          }

        override fun OutputStream.serialize(operation: ContentsOperation.SetVersion, enumerator: DataEnumerator<String>): Unit =
          DataOutputStream(this).run {
            writeInt(operation.version)
            writeResult(operation.result)
          }
      }

      override fun toString(): String {
        return "SetVersion(version=$version, result=$result)"
      }
    }

    companion object {
      val <T: Any> ContentsOperation<T>.contentRecordId: Int? get() = when (this) {
        is AcquireNewRecord -> if (result.isSuccess) result.value else null
        is AcquireRecord -> recordId
        is AppendStream -> recordId
        is ReleaseRecord -> recordId
        is ReplaceBytes -> recordId
        is WriteBytes -> recordId
        is WriteStream -> recordId
        is WriteStream2 -> recordId
        is SetVersion -> null
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

          override fun OutputStream.serialize(operation: ContentChange, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
          }
        }

        override fun toString(): String {
          return "EventStart.ContentChange(fileId=$fileId, eventTimestamp=$eventTimestamp, result=$result)"
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

          override fun OutputStream.serialize(operation: Copy, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
            writeInt(operation.newParentId)
          }
        }

        override fun toString(): String {
          return "EventStart.Copy(fileId=$fileId, newParentId=$newParentId, eventTimestamp=$eventTimestamp, result=$result)"
        }
      }

      class Create(
        eventTimestamp: Long,
        val parentId: Int,
        val newChildName: PayloadRef, // TODO: is it really needed?
        val isDirectory: Boolean
      ) : EventStart(VfsOperationTag.VFILE_EVENT_CREATE, eventTimestamp), PayloadContainingOperation {
        override val dataRef: PayloadRef get() = newChildName

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

          override fun OutputStream.serialize(operation: Create, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.parentId)
            writePayloadRef(operation.newChildName)
            writeBoolean(operation.isDirectory)
          }
        }

        override fun toString(): String {
          return "EventStart.Create(parentId=$parentId, newChildName=$newChildName, isDirectory=$isDirectory, eventTimestamp=$eventTimestamp, result=$result)"
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

          override fun OutputStream.serialize(operation: Delete, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
          }
        }

        override fun toString(): String {
          return "EventStart.Delete(fileId=$fileId, eventTimestamp=$eventTimestamp, result=$result)"
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

          override fun OutputStream.serialize(operation: Move, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
            writeInt(operation.oldParentId)
            writeInt(operation.newParentId)
          }
        }

        override fun toString(): String {
          return "EventStart.Move(fileId=$fileId, oldParentId=$oldParentId, newParentId=$newParentId, eventTimestamp=$eventTimestamp, result=$result)"
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

          override fun OutputStream.serialize(operation: PropertyChange, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
            writeLong(operation.eventTimestamp)
            writeInt(operation.fileId)
            writeInt(enumerator.enumerate(operation.propertyName))
          }
        }

        override fun toString(): String {
          return "EventStart.PropertyChange(fileId=$fileId, propertyName='$propertyName', eventTimestamp=$eventTimestamp, result=$result)"
        }
      }
    }

    class EventEnd(val eventTag: VfsOperationTag)
      : VFileEventOperation<Unit>(VfsOperationTag.VFILE_EVENT_END, fromValue(Unit)) {
      internal companion object : Serializer<EventEnd> {
        override val valueSizeBytes: Int = 1
        override fun InputStream.deserialize(enumerator: DataEnumerator<String>): EventEnd =
          DataInputStream(this).run {
            val tag = readByte()
            return EventEnd(VfsOperationTag.entries[tag.toInt()].also {
              if (!it.isVFileEventStartOperation) {
                throw IllegalStateException("unexpected EventEnd tag: $it")
              }
            })
          }

        override fun OutputStream.serialize(operation: EventEnd, enumerator: DataEnumerator<String>): Unit = DataOutputStream(this).run {
          writeByte(operation.eventTag.ordinal)
        }
      }

      override fun toString(): String {
        return "EventEnd(eventTag=$eventTag)"
      }
    }
  }

  companion object {
    private inline fun <reified T : Any> DataInputStream.readResult() =
      OperationResult.deserialize<T>(readInt())

    private inline fun <reified T : Any> DataOutputStream.writeResult(result: OperationResult<T>) =
      writeInt(result.serialize())
  }
}

@Suppress("UNCHECKED_CAST")
fun <O : VfsOperation<*>> VfsOperation.Serializer<O>.serializeOperation(operation: VfsOperation<*>,
                                                                        enumerator: DataEnumerator<String>,
                                                                        out: OutputStream): Unit =
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