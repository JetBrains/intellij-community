// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.*
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.AttributesOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.ContentsOperation.Companion.contentRecordId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag.*
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.AttributeDataRule.AttributeOverwriteData

object VfsModificationContract {
  /**
   * @param relevantOperations a mask of operations that can possibly modify the property
   * @param ifModifies invokes `modify` argument with a modification value `T` if all additional conditions are met
   */
  interface ModificationRule<T> {
    val relevantOperations: VfsOperationTagsMask
    val modifier: ConditionalVfsModifier<T>
  }

  inline val <T> ModificationRule<T>.isRelevantAndModifies get() = modifier.precondition { relevantOperations.contains(tag) }

  class PropertyOverwriteRule<T>(
    override val relevantOperations: VfsOperationTagsMask,
    val propertyModifier: VfsOperation<*>.(setValue: (T) -> Unit) -> Unit,
  ) : ModificationRule<T> {
    init {
      assert(relevantOperations.toList().all { it.isRecordOperation })
    }

    override val modifier get() = propertyModifier

    companion object {
      fun <T> PropertyOverwriteRule<T>.forFileId(fileId: Int): ConditionalVfsModifier<T> = isRelevantAndModifies.andIf {
        (this as RecordsOperation<*>).fileId == fileId
      }
    }
  }

  val nameId = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_NAME_ID, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0)
      is RecordsOperation.SetNameId -> setValue(nameId)
      is RecordsOperation.FillRecord -> setValue(nameId)
      is RecordsOperation.CleanRecord -> setValue(0)
      else -> throw AssertionError("operation $this does not overwrite nameId property")
    }
  }

  val parentId = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_PARENT, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0)
      is RecordsOperation.SetParent -> setValue(parentId)
      is RecordsOperation.FillRecord -> setValue(parentId)
      is RecordsOperation.CleanRecord -> setValue(0)
      else -> throw AssertionError("operation $this does not overwrite parentId property")
    }
  }

  val length = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_LENGTH, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0L)
      is RecordsOperation.SetLength -> setValue(length)
      is RecordsOperation.FillRecord -> setValue(length)
      is RecordsOperation.CleanRecord -> setValue(0L)
      else -> throw AssertionError("operation $this does not overwrite length property")
    }
  }

  val timestamp = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_TIMESTAMP, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0L)
      is RecordsOperation.SetTimestamp -> setValue(timestamp)
      is RecordsOperation.FillRecord -> setValue(timestamp)
      is RecordsOperation.CleanRecord -> setValue(0L)
      else -> throw AssertionError("operation $this does not overwrite timestamp property")
    }
  }

  val flags = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_FLAGS, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0)
      is RecordsOperation.SetFlags -> setValue(flags)
      is RecordsOperation.FillRecord -> setValue(flags)
      is RecordsOperation.CleanRecord -> setValue(0)
      else -> throw AssertionError("operation $this does not overwrite flags property")
    }
  }

  val contentRecordId = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_CONTENT_RECORD_ID, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0)
      is RecordsOperation.SetContentRecordId -> setValue(recordId)
      // it is not written explicitly in the code, because fillRecord is only used in two places where contentRecordId has no effect anyway,
      // but semantically it should be treated like contentRecordId=0
      is RecordsOperation.FillRecord -> setValue(0)
      is RecordsOperation.CleanRecord -> setValue(0)
      else -> throw AssertionError("operation $this does not overwrite contentRecordId property")
    }
  }

  val attributeRecordId = PropertyOverwriteRule(
    VfsOperationTagsMask(REC_ALLOC, REC_SET_ATTR_REC_ID, REC_FILL_RECORD, REC_CLEAN_RECORD)
  ) { setValue ->
    when (this) {
      is RecordsOperation.AllocateRecord -> setValue(0)
      is RecordsOperation.SetAttributeRecordId -> setValue(recordId)
      is RecordsOperation.FillRecord -> if (overwriteAttrRef) setValue(0)
      is RecordsOperation.CleanRecord -> setValue(0)
      else -> throw AssertionError("operation $this does not overwrite attributeRecordId property")
    }
  }

  class ContentModificationRule(
    override val relevantOperations: VfsOperationTagsMask,
    private val contentModifier: VfsOperation<*>.(modifyContent: (ContentOperation) -> Unit) -> Unit
  ) : ModificationRule<ContentOperation> {
    init {
      assert(relevantOperations.toList().all { it.isContentOperation })
    }

    override val modifier get() = contentModifier

    companion object {
      fun ContentModificationRule.forContentRecordId(contentRecordId: Int) = isRelevantAndModifies.andIf {
        (this as ContentsOperation<*>).contentRecordId == contentRecordId
      }
    }
  }

  /** reference counting is not supported, because it is not used anymore */
  sealed interface ContentOperation {
    fun interface Set : ContentOperation {
      fun readContent(payloadReader: PayloadReader): State.DefinedState<ByteArray>
    }

    fun interface Modify : ContentOperation {
      fun modifyContent(previousContent: ByteArray,
                        payloadReader: PayloadReader): State.DefinedState<ByteArray>
    }
  }


  /** reference counting is not supported, because it is not used anymore */
  val content = ContentModificationRule(
    VfsOperationTagsMask(CONTENT_ACQUIRE_NEW_RECORD, CONTENT_WRITE_BYTES,
                         CONTENT_WRITE_STREAM, CONTENT_WRITE_STREAM_2,
                         CONTENT_REPLACE_BYTES, CONTENT_APPEND_STREAM)
  ) { modifyContent ->
    val setContent = { dataPayloadRef: PayloadRef ->
      ContentOperation.Set { payloadReader ->
        payloadReader(dataPayloadRef)
      }
    }
    when (this) {
      is ContentsOperation.AcquireNewRecord -> modifyContent(
        ContentOperation.Set { ByteArray(0).let(State::Ready) }
      )
      is ContentsOperation.WriteBytes -> modifyContent(setContent(dataRef))
      is ContentsOperation.WriteStream -> modifyContent(setContent(dataRef))
      is ContentsOperation.WriteStream2 -> modifyContent(setContent(dataRef))
      is ContentsOperation.ReplaceBytes -> {
        modifyContent(ContentOperation.Modify { before, payloadReader ->
          payloadReader(dataRef).fmap { data ->
            if (offset < 0 || offset + data.size > before.size) { // from AbstractStorage.replaceBytes
              throw VfsRecoveryException(
                "replaceBytes: replace is out of bounds: " +
                "offset=${offset} data.size=${data.size} before.size=${before.size}")
            }
            else {
              before.copyOfRange(0, offset) + data + before.copyOfRange(offset + data.size, before.size)
            }
          }
        })
      }
      is ContentsOperation.AppendStream -> {
        modifyContent(ContentOperation.Modify { before, payloadReader ->
          payloadReader(dataRef).fmap { before + it }
        })
      }
      else -> throw AssertionError("operation $this does not modify content")
    }
  }

  class AttributeDataRule(
    override val relevantOperations: VfsOperationTagsMask,
    private val attributeDataModifier: VfsOperation<*>.(overwriteAttributeData: (AttributeOverwriteData) -> Unit) -> Unit
  ) : ModificationRule<AttributeOverwriteData> {
    init {
      assert(relevantOperations.toList().all { it.isAttributeOperation || it.isRecordOperation })
    }

    class AttributeOverwriteData(val enumeratedAttributeFilter: EnumeratedFileAttribute?, val data: PayloadRef?) {
      fun affectsAttribute(enumeratedAttr: EnumeratedFileAttribute) =
        enumeratedAttributeFilter == null || enumeratedAttributeFilter == enumeratedAttr
    }

    override val modifier get() = attributeDataModifier

    companion object {
      fun AttributeDataRule.forFileId(fileId: Int) = isRelevantAndModifies.andIf {
        (this as AttributesOperation<*>).fileId == fileId
      }
    }
  }

  val attributeData = AttributeDataRule(
    VfsOperationTagsMask(ATTR_DELETE_ATTRS, ATTR_WRITE_ATTR, REC_ALLOC)
  ) { overwriteAttributeData ->
    when (this) {
      is RecordsOperation.AllocateRecord -> overwriteAttributeData(AttributeOverwriteData(null, null))
      is AttributesOperation.DeleteAttributes -> overwriteAttributeData(AttributeOverwriteData(null, null))
      is AttributesOperation.WriteAttribute -> overwriteAttributeData(AttributeOverwriteData(enumeratedAttribute, dataRef))
      else -> throw AssertionError("operation $this does not modify attribute's data")
    }
  }
}