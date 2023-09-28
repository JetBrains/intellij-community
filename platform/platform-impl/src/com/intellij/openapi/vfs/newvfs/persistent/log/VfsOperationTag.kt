// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.nextIncomplete
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.*
import com.intellij.util.io.DataEnumerator
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

enum class VfsOperationTag(val operationSerializer: Serializer<*>) {
  NULL(nullSerializer),

  REC_ALLOC(RecordsOperation.AllocateRecord),
  REC_SET_ATTR_REC_ID(RecordsOperation.SetAttributeRecordId),
  REC_SET_CONTENT_RECORD_ID(RecordsOperation.SetContentRecordId),
  REC_SET_PARENT(RecordsOperation.SetParent),
  REC_SET_NAME_ID(RecordsOperation.SetNameId),
  REC_SET_FLAGS(RecordsOperation.SetFlags),
  REC_SET_LENGTH(RecordsOperation.SetLength),
  REC_SET_TIMESTAMP(RecordsOperation.SetTimestamp),
  REC_MARK_RECORD_AS_MODIFIED(RecordsOperation.MarkRecordAsModified),
  REC_FILL_RECORD(RecordsOperation.FillRecord),
  REC_CLEAN_RECORD(RecordsOperation.CleanRecord),
  REC_SET_VERSION(RecordsOperation.SetVersion),

  ATTR_WRITE_ATTR(AttributesOperation.WriteAttribute),
  ATTR_DELETE_ATTRS(AttributesOperation.DeleteAttributes),
  ATTR_SET_VERSION(AttributesOperation.SetVersion),

  CONTENT_WRITE_BYTES(ContentsOperation.WriteBytes),
  CONTENT_WRITE_STREAM(ContentsOperation.WriteStream),
  CONTENT_WRITE_STREAM_2(ContentsOperation.WriteStream2),
  CONTENT_APPEND_STREAM(ContentsOperation.AppendStream),
  CONTENT_REPLACE_BYTES(ContentsOperation.ReplaceBytes),
  CONTENT_ACQUIRE_NEW_RECORD(ContentsOperation.AcquireNewRecord),
  CONTENT_ACQUIRE_RECORD(ContentsOperation.AcquireRecord),
  CONTENT_RELEASE_RECORD(ContentsOperation.ReleaseRecord),
  CONTENT_SET_VERSION(ContentsOperation.SetVersion),

  VFILE_EVENT_CONTENT_CHANGE(VFileEventOperation.EventStart.ContentChange),
  VFILE_EVENT_COPY(VFileEventOperation.EventStart.Copy),
  VFILE_EVENT_CREATE(VFileEventOperation.EventStart.Create),
  VFILE_EVENT_DELETE(VFileEventOperation.EventStart.Delete),
  VFILE_EVENT_MOVE(VFileEventOperation.EventStart.Move),
  VFILE_EVENT_PROPERTY_CHANGED(VFileEventOperation.EventStart.PropertyChange),
  VFILE_EVENT_END(VFileEventOperation.EventEnd);

  companion object {
    const val SIZE_BYTES: Int = Byte.SIZE_BYTES
  }
}

private val nullSerializer = object : Serializer<Nothing> {
  private val msg = "tried to access NULL descriptor serializer"
  override val valueSizeBytes
    get() = throw IllegalAccessException(msg)

  override fun InputStream.deserialize(enumerator: DataEnumerator<String>) = throw IllegalAccessException(msg)
  override fun OutputStream.serialize(operation: Nothing, enumerator: DataEnumerator<String>) = throw IllegalAccessException(msg)
}

val VfsOperationTag.isRecordOperation: Boolean get() = VfsOperationTag.REC_ALLOC <= this && this <= VfsOperationTag.REC_SET_VERSION
val VfsOperationTag.isAttributeOperation: Boolean get() = VfsOperationTag.ATTR_WRITE_ATTR <= this && this <= VfsOperationTag.ATTR_SET_VERSION
val VfsOperationTag.isContentOperation: Boolean get() = VfsOperationTag.CONTENT_WRITE_BYTES <= this && this <= VfsOperationTag.CONTENT_SET_VERSION
val VfsOperationTag.isVFileEventStartOperation: Boolean get() = VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE <= this && this <= VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED
val VfsOperationTag.isVFileEventOperation: Boolean get() = VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE <= this && this <= VfsOperationTag.VFILE_EVENT_END

@JvmInline
value class VfsOperationTagsMask(val mask: Long) {
  constructor(vararg tags: VfsOperationTag) : this(tags.map { 1L shl it.ordinal }.fold(0L, Long::or))

  operator fun contains(tag: VfsOperationTag): Boolean = (mask and (1L shl tag.ordinal)) != 0L

  fun toList(): List<VfsOperationTag> = VfsOperationTag.entries.filter { contains(it) }

  companion object {
    /**
     * @see [IteratorUtils.nextIncomplete]
     */
    val EMPTY: VfsOperationTagsMask = VfsOperationTagsMask(0L)
    val ALL: VfsOperationTagsMask = VfsOperationTagsMask(*VfsOperationTag.entries.toTypedArray())
    val RecordsMask: VfsOperationTagsMask =
      VfsOperationTagsMask(*VfsOperationTag.entries.filter { it.isRecordOperation }.toTypedArray())
    val AttributesMask: VfsOperationTagsMask =
      VfsOperationTagsMask(*VfsOperationTag.entries.filter { it.isAttributeOperation }.toTypedArray())
    val ContentsMask: VfsOperationTagsMask =
      VfsOperationTagsMask(*VfsOperationTag.entries.filter { it.isContentOperation }.toTypedArray())
    val VFileEventsMask: VfsOperationTagsMask =
      VfsOperationTagsMask(*VfsOperationTag.entries.filter { it.isVFileEventOperation }.toTypedArray())
    val VFileEventsStartMask: VfsOperationTagsMask =
      VfsOperationTagsMask(*VfsOperationTag.entries.filter { it.isVFileEventStartOperation }.toTypedArray())
    val VFileEventEndMask: VfsOperationTagsMask = VfsOperationTagsMask(VfsOperationTag.VFILE_EVENT_END)

    fun List<VfsOperationTagsMask>.intersection(): VfsOperationTagsMask =
      VfsOperationTagsMask(map { it.mask }.foldRight(ALL.mask, Long::and))

    fun List<VfsOperationTagsMask>.union(): VfsOperationTagsMask =
      VfsOperationTagsMask(map { it.mask }.foldRight(EMPTY.mask, Long::or))

    // FIXME remove reflection here
    // if operation's tag is in this mask, then it's implementing PayloadContainingOperation
    val PayloadContainingOperations: VfsOperationTagsMask = run {
      fun KClass<*>.collectAllFinalSubclasses(): Sequence<KClass<*>> = sequence {
        nestedClasses.forEach {
          if (it.isFinal) yield(it)
          else yieldAll(it.collectAllFinalSubclasses())
        }
      }
      val payloadContainingSerializers = VfsOperation::class.collectAllFinalSubclasses().mapNotNull {
        if (it.java.interfaces.contains(PayloadContainingOperation::class.java))
          it.companionObjectInstance!! as Serializer<*>
        else null
      }.toSet()
      VfsOperationTagsMask(*VfsOperationTag.entries.filter { it.operationSerializer in payloadContainingSerializers }.toTypedArray())
    }
  }
}