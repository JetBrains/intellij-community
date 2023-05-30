// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.movableIn
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.moveFiltered
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.*
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag.*
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.PropertyOverwriteContract.OverwriteRule.Companion.forFileId
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.NotEnoughInformationCause
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.VfsRecoveryException
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.RecoveredChildrenIds

object VfsChronicle {
  /*
   * lookup... methods traverse operations log in a given direction and return a filled LookupResult if evidence was found
   * that a property was set to a contained value in that range (first such event in traversal order)
   */

  inline fun lookupNameId(iterator: OperationLogStorage.Iterator,
                          fileId: Int,
                          direction: TraverseDirection = TraverseDirection.REWIND,
                          crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.nameId, stopIf)

  inline fun lookupParentId(iterator: OperationLogStorage.Iterator,
                            fileId: Int,
                            direction: TraverseDirection = TraverseDirection.REWIND,
                            crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.parentId, stopIf)

  inline fun lookupLength(iterator: OperationLogStorage.Iterator,
                          fileId: Int,
                          direction: TraverseDirection = TraverseDirection.REWIND,
                          crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Long> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.length, stopIf)

  inline fun lookupTimestamp(iterator: OperationLogStorage.Iterator,
                             fileId: Int,
                             direction: TraverseDirection = TraverseDirection.REWIND,
                             crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Long> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.timestamp, stopIf)

  inline fun lookupFlags(iterator: OperationLogStorage.Iterator,
                         fileId: Int,
                         direction: TraverseDirection = TraverseDirection.REWIND,
                         crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.flags, stopIf)

  inline fun lookupContentRecordId(iterator: OperationLogStorage.Iterator,
                                   fileId: Int,
                                   direction: TraverseDirection = TraverseDirection.REWIND,
                                   crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.contentRecordId, stopIf)

  inline fun lookupAttributeRecordId(iterator: OperationLogStorage.Iterator,
                                     fileId: Int,
                                     direction: TraverseDirection = TraverseDirection.REWIND,
                                     crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, PropertyOverwriteContract.attributeRecordId, stopIf)

  sealed interface ContentOperation {
    fun interface Set : ContentOperation {
      fun readContent(payloadReader: (PayloadRef) -> State.DefinedState<ByteArray>): State.DefinedState<ByteArray>
    }

    fun interface Modify : ContentOperation {
      fun modifyContent(previousContent: ByteArray,
                        payloadReader: (PayloadRef) -> State.DefinedState<ByteArray>): State.DefinedState<ByteArray>
    }
  }

  fun lookupContentOperation(iterator: OperationLogStorage.Iterator,
                             contentRecordId: Int,
                             direction: TraverseDirection = TraverseDirection.REWIND,
                             stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<ContentOperation> {
    val rewriteContentCase: (Int, PayloadRef) -> ContentOperation? = { recordId: Int, dataPayloadRef: PayloadRef ->
      if (recordId == contentRecordId) ContentOperation.Set { payloadReader -> payloadReader(dataPayloadRef) }
      else null
    }
    return traverseOperationsLogForLookup(
      iterator, direction,
      VfsOperationTagsMask(CONTENT_ACQUIRE_NEW_RECORD, CONTENT_WRITE_BYTES,
                           CONTENT_WRITE_STREAM, CONTENT_WRITE_STREAM_2,
                           CONTENT_REPLACE_BYTES, CONTENT_APPEND_STREAM),
      stopIf,
      onComplete = { operation, detect ->
        when (operation) {
          is ContentsOperation.AcquireNewRecord ->
            if (operation.result.hasValue && operation.result.value == contentRecordId) {
              detect(ContentOperation.Set { ByteArray(0).let(State::Ready) })
            }
          is ContentsOperation.WriteBytes -> rewriteContentCase(operation.recordId, operation.dataPayloadRef)?.let(detect)
          is ContentsOperation.WriteStream -> rewriteContentCase(operation.recordId, operation.dataPayloadRef)?.let(detect)
          is ContentsOperation.WriteStream2 -> rewriteContentCase(operation.recordId, operation.dataPayloadRef)?.let(detect)
          is ContentsOperation.ReplaceBytes -> {
            if (operation.recordId == contentRecordId) {
              detect(ContentOperation.Modify { before, payloadReader ->
                payloadReader(operation.dataPayloadRef).fmap { data ->
                  if (operation.offset < 0 || operation.offset + data.size > before.size) { // from AbstractStorage.replaceBytes
                    throw VfsRecoveryException("replaceBytes: replace is out of bounds: " +
                                               "offset=${operation.offset} data.size=${data.size} before.size=${before.size}")
                  }
                  else {
                    before.copyOfRange(0, operation.offset) +
                    data +
                    before.copyOfRange(operation.offset + data.size, before.size)
                  }
                }
              })
            }
          }
          is ContentsOperation.AppendStream -> {
            if (operation.recordId == contentRecordId) {
              detect(ContentOperation.Modify { before, payloadReader ->
                payloadReader(operation.dataPayloadRef).fmap { before + it }
              })
            }
          }
          else -> throw IllegalStateException("filtered read is broken")
        }
      })
  }

  fun restoreContent(iterator: OperationLogStorage.Iterator,
                     contentRecordId: Int,
                     payloadReader: (PayloadRef) -> State.DefinedState<ByteArray>,
                     stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): State.DefinedState<ByteArray> {
    if (contentRecordId == 0) return State.NotAvailable(NotEnoughInformationCause("VFS didn't cache file's content"))
    val restoreStack = mutableListOf<ContentOperation.Modify>()
    while (iterator.hasPrevious() && !stopIf(iterator)) {
      val lookup =
        lookupContentOperation(iterator, contentRecordId, TraverseDirection.REWIND, stopIf)
      if (!lookup.found) return State.NotAvailable() // didn't find any relevant content op
      when (val op = lookup.value) {
        is ContentOperation.Modify -> restoreStack.add(op)
        is ContentOperation.Set -> {
          return restoreStack.foldRight(op.readContent(payloadReader)) { modOp, data ->
            data.bind { modOp.modifyContent(it, payloadReader) }
          }
        }
      }
    }
    // no ContentOp.SetValue was found
    return State.NotAvailable()
  }

  /**
   * @return `null` if attributes were deleted
   */
  inline fun lookupAttributeData(iterator: OperationLogStorage.Iterator,
                                 fileId: Int,
                                 enumeratedAttrId: Int,
                                 direction: TraverseDirection = TraverseDirection.REWIND,
                                 crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<PayloadRef?> =
    traverseOperationsLogForLookup(
      iterator, direction, VfsOperationTagsMask(ATTR_DELETE_ATTRS, ATTR_WRITE_ATTR), stopIf,
      onComplete = { operation, detect ->
        when (operation) {
          is AttributesOperation.DeleteAttributes -> if (operation.fileId == fileId) detect(null)
          is AttributesOperation.WriteAttribute ->
            if (operation.fileId == fileId && operation.attributeIdEnumerated == enumeratedAttrId) detect(operation.attrDataPayloadRef)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  private class RecoveredChildrenIdsImpl(val ids: List<Int>, override val isComplete: Boolean) : RecoveredChildrenIds, List<Int> by ids

  fun restoreChildrenIds(iterator: OperationLogStorage.Iterator, fileId: Int): RecoveredChildrenIds {
    val childrenIds = mutableSetOf<Int>()
    val seenDifferentSetParent = mutableSetOf<Int>()
    traverseOperationsLog(
      iterator, TraverseDirection.REWIND, VfsOperationTagsMask(REC_ALLOC, REC_SET_PARENT, REC_FILL_RECORD, REC_CLEAN_RECORD)
    ) {
      when (it) {
        is RecordsOperation.AllocateRecord -> {
          if (it.result.hasValue) {
            val id = it.result.value
            if (fileId == id) return RecoveredChildrenIdsImpl(childrenIds.toList(), true)
            seenDifferentSetParent.add(id)
          }
        }
        is RecordsOperation.SetParent -> {
          if (it.parentId == fileId && !seenDifferentSetParent.contains(it.fileId)) {
            childrenIds.add(it.fileId)
          }
          else {
            seenDifferentSetParent.add(it.fileId)
          }
        }
        is RecordsOperation.FillRecord -> {
          if (it.parentId == fileId && !seenDifferentSetParent.contains(it.fileId)) {
            childrenIds.add(it.fileId)
          }
          else {
            seenDifferentSetParent.add(it.fileId)
          }
        }
        is RecordsOperation.CleanRecord -> {
          seenDifferentSetParent.add(it.fileId)
        }
        else -> throw IllegalStateException("filtered read is broken")
      }
    }
    return RecoveredChildrenIdsImpl(childrenIds.toList(), false)
  }

  /**
   * @see [restoreChildrenIds]
   */
  fun restoreRootIds(iterator: OperationLogStorage.Iterator): RecoveredChildrenIds = restoreChildrenIds(iterator, 1) // ROOT_FILE_ID

  // traversing utilities

  interface TraverseContext {
    fun stop()
    fun isStopped(): Boolean
  }

  class TraverseContextImpl : TraverseContext {
    private var stopFlag = false

    override fun stop() {
      stopFlag = true
    }

    override fun isStopped(): Boolean = stopFlag
  }

  // allows T to be nullable
  interface LookupResult<T> {
    val found: Boolean

    /**
     * @throws IllegalStateException if [found] is false
     */
    val value: T

    companion object {
      inline fun <T, R> LookupResult<T>.mapCases(onNotFound: () -> R, onFound: (value: T) -> R): R =
        if (found) onFound(value) else onNotFound()

      fun <T> LookupResult<T>.getOrNull(): T? = mapCases({ null }) { it }

      inline fun <T> LookupResult<T>.toState(
        notFoundCause: () -> NotEnoughInformationCause = { State.Companion.UnspecifiedNotAvailableException }
      ): State.DefinedState<T> = mapCases({ State.NotAvailable(notFoundCause()) }, State::Ready)
    }
  }

  class LookupResultImpl<T> : LookupResult<T> {
    override var found: Boolean = false
    private var _value: T? = null

    @Suppress("UNCHECKED_CAST")
    override val value: T
      get() = if (found) {
        _value as T
      }
      else {
        throw IllegalStateException("Lookup didn't succeed")
      }

    fun detect(value: T) {
      if (!found) {
        _value = value
        found = true
      }
    }
  }

  /**
   * Default behaviour:
   *
   * [onInvalid] throws the cause -- some unexpected exception happened in [OperationLogStorage], can't do anything about it;
   *
   * [onIncomplete] -- if the operation's tag is contained in [toReadMask], then we're interested in it, but it is Incomplete => information
   *                    about the operation has been lost, better to stop the traverse; if it's not contained, then there are two cases:
   *                    1. an operation is actually [OperationReadResult.Complete], but due to [toReadMask] filter it was not read completely,
   *                    2. it is actually [OperationReadResult.Incomplete] -- both cases shouldn't affect our computation anyway
   *                    (this is a subtle place as one may want to stop the traverse at any Incomplete so this needs to be carefully
   *                    revised at the usage site);
   *
   * [onCompleteExceptional] -- operation's tag is contained in [toReadMask], but the operation has finished with an exception, stop the traverse
   */
  inline fun traverseOperationsLog(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    toReadMask: VfsOperationTagsMask,
    crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false },
    onInvalid: TraverseContext.(cause: Throwable) -> Unit = { throw it },
    onIncomplete: TraverseContext.(tag: VfsOperationTag) -> Unit = { if (toReadMask.contains(it)) stop() },
    onCompleteExceptional: TraverseContext.(operation: VfsOperation<*>) -> Unit = { stop() },
    onComplete: TraverseContext.(operation: VfsOperation<*>) -> Unit
  ) = TraverseContextImpl().run {
    while (iterator.movableIn(direction) && !stopIf(iterator) && !isStopped()) {
      when (val read = iterator.moveFiltered(direction, toReadMask)) {
        is OperationReadResult.Invalid -> onInvalid(read.cause)
        is OperationReadResult.Incomplete -> onIncomplete(read.tag)
        is OperationReadResult.Complete -> {
          if (read.operation.result.hasValue) onComplete(read.operation)
          else onCompleteExceptional(read.operation)
        }
      }
    }
  }

  /**
   * Default behaviour is same as [traverseOperationsLog].
   * @param onComplete `detect` must be called to succeed the lookup, traversal will stop afterward
   */
  inline fun <T> traverseOperationsLogForLookup(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    toReadMask: VfsOperationTagsMask,
    crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false },
    onInvalid: TraverseContext.(cause: Throwable) -> Unit = { throw it },
    onIncomplete: TraverseContext.(tag: VfsOperationTag) -> Unit = { if (toReadMask.contains(it)) stop() },
    onCompleteExceptional: TraverseContext.(operation: VfsOperation<*>, detect: (T) -> Unit) -> Unit = { _, _ -> stop() },
    onComplete: TraverseContext.(operation: VfsOperation<*>, detect: (T) -> Unit) -> Unit
  ): LookupResult<T> {
    val result = LookupResultImpl<T>()
    traverseOperationsLog(
      iterator, direction, toReadMask, { result.found || stopIf(it) }, onInvalid, onIncomplete,
      onCompleteExceptional = { op -> onCompleteExceptional(op, result::detect) },
      onComplete = { op -> onComplete(op, result::detect) }
    )
    return result
  }

  /**
   * Default behaviour is same as [traverseOperationsLogForLookup].
   */
  inline fun <T> traverseOperationsLogForPropertyOverwriteLookup(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    fileId: Int,
    propertyRule: PropertyOverwriteContract.OverwriteRule<T>,
    crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false },
    onInvalid: TraverseContext.(cause: Throwable) -> Unit = { throw it },
    onIncomplete: TraverseContext.(tag: VfsOperationTag) -> Unit = { if (propertyRule.relatedOperations.contains(it)) stop() },
    onCompleteExceptional: TraverseContext.(operation: VfsOperation<*>, detect: (T) -> Unit) -> Unit = { _, _ -> stop() },
  ): LookupResult<T> {
    val ifOverwrites = propertyRule.forFileId(fileId)
    return traverseOperationsLogForLookup(
      iterator, direction, propertyRule.relatedOperations, stopIf,
      onInvalid, onIncomplete, onCompleteExceptional,
      onComplete = { op, detect -> op.ifOverwrites(detect) }
    )
  }

  object PropertyOverwriteContract {
    /**
     * This a convenience class to keep logic about VFS transformations together
     * @param relatedOperations a mask of operations that can possibly overwrite the property
     */
    class OverwriteRule<T>(
      val relatedOperations: VfsOperationTagsMask,
      val ifOverwrites: VfsOperation<*>.(setValue: (T) -> Unit) -> Unit,
    ) {
      init {
        assert(relatedOperations.toList().all { it.isRecordOperation })
      }

      companion object {
        fun <T> OverwriteRule<T>.forFileId(fileId: Int): VfsOperation<*>.(setValue: (T) -> Unit) -> Unit = { setValue ->
          if (relatedOperations.contains(tag) && (this as RecordsOperation<*>).fileId == fileId) {
            ifOverwrites(setValue)
          }
        }
      }
    }

    val nameId = OverwriteRule(
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

    val parentId = OverwriteRule(
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

    val length = OverwriteRule(
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

    val timestamp = OverwriteRule(
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

    val flags = OverwriteRule(
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

    val contentRecordId = OverwriteRule(
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

    val attributeRecordId = OverwriteRule(
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
  }
}