// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.movableIn
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.moveFiltered
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.ContentsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag.*
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.NotEnoughInformationCause
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.VfsRecoveryException

object VfsChronicle {

  /*
   * lookup... methods traverse operations log in a given direction and return a filled LookupResult if evidence was found
   * that a property was set to a contained value in that range (first such event in traversal order)
   */

  // TODO: lookup methods can throw on Invalid, it should be caught and processed in usages

  inline fun lookupNameId(iterator: OperationLogStorage.Iterator,
                          fileId: Int,
                          direction: TraverseDirection = TraverseDirection.REWIND,
                          crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Int> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_NAME_ID, REC_FILL_RECORD, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0)
          is RecordsOperation.SetNameId -> if (operation.fileId == fileId) detect(operation.nameId)
          is RecordsOperation.FillRecord -> if (operation.fileId == fileId) detect(operation.nameId)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  inline fun lookupParentId(iterator: OperationLogStorage.Iterator,
                            fileId: Int,
                            direction: TraverseDirection = TraverseDirection.REWIND,
                            crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Int> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_PARENT, REC_FILL_RECORD, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0)
          is RecordsOperation.SetParent -> if (operation.fileId == fileId) detect(operation.parentId)
          is RecordsOperation.FillRecord -> if (operation.fileId == fileId) detect(operation.parentId)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  inline fun lookupLength(iterator: OperationLogStorage.Iterator,
                          fileId: Int,
                          direction: TraverseDirection = TraverseDirection.REWIND,
                          crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Long> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_LENGTH, REC_FILL_RECORD, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0L)
          is RecordsOperation.SetLength -> if (operation.fileId == fileId) detect(operation.length)
          is RecordsOperation.FillRecord -> if (operation.fileId == fileId) detect(operation.length)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0L)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  inline fun lookupTimestamp(iterator: OperationLogStorage.Iterator,
                             fileId: Int,
                             direction: TraverseDirection = TraverseDirection.REWIND,
                             crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Long> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_TIMESTAMP, REC_FILL_RECORD, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0L)
          is RecordsOperation.SetTimestamp -> if (operation.fileId == fileId) detect(operation.timestamp)
          is RecordsOperation.FillRecord -> if (operation.fileId == fileId) detect(operation.timestamp)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0L)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  inline fun lookupFlags(iterator: OperationLogStorage.Iterator,
                         fileId: Int,
                         direction: TraverseDirection = TraverseDirection.REWIND,
                         crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Int> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_FLAGS, REC_FILL_RECORD, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0)
          is RecordsOperation.SetFlags -> if (operation.fileId == fileId) detect(operation.flags)
          is RecordsOperation.FillRecord -> if (operation.fileId == fileId) detect(operation.flags)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  inline fun lookupContentRecordId(iterator: OperationLogStorage.Iterator,
                                   fileId: Int,
                                   direction: TraverseDirection = TraverseDirection.REWIND,
                                   crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Int> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_CONTENT_RECORD_ID, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0)
          is RecordsOperation.SetContentRecordId -> if (operation.fileId == fileId) detect(operation.recordId)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  inline fun lookupAttributeRecordId(iterator: OperationLogStorage.Iterator,
                                     fileId: Int,
                                     direction: TraverseDirection = TraverseDirection.REWIND,
                                     crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<Int> =
    traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(REC_ALLOC, REC_SET_ATTR_REC_ID, REC_FILL_RECORD, REC_CLEAN_RECORD),
      onValid = { operation, detect ->
        when (operation) {
          is RecordsOperation.AllocateRecord -> if (operation.result.hasValue && operation.result.value == fileId) detect(0)
          is RecordsOperation.SetAttributeRecordId -> if (operation.fileId == fileId) detect(operation.recordId)
          is RecordsOperation.FillRecord -> if (operation.fileId == fileId && operation.overwriteAttrRef) detect(0)
          is RecordsOperation.CleanRecord -> if (operation.fileId == fileId) detect(0)
          else -> throw IllegalStateException("filtered read is broken")
        }
      })

  sealed interface ContentOperation {
    class SetValue(val data: ByteArray) : ContentOperation
    class Modify(val modify: (ByteArray) -> ByteArray) : ContentOperation

    /**
     * Some operation is there, but recovery is not possible
     */
    class NotAvailable(val cause: NotEnoughInformationCause) : ContentOperation
  }

  fun lookupContentOperation(iterator: OperationLogStorage.Iterator,
                             contentRecordId: Int,
                             payloadReader: (PayloadRef) -> ByteArray?,
                             direction: TraverseDirection = TraverseDirection.REWIND,
                             condition: (OperationLogStorage.Iterator) -> Boolean = { true }): LookupResult<ContentOperation> {
    val rewriteContentCase: (Int, PayloadRef) -> ContentOperation? = { recordId: Int, dataPayloadRef: PayloadRef ->
      if (recordId == contentRecordId) {
        val data = payloadReader(dataPayloadRef)
        if (data == null) ContentOperation.NotAvailable(NotEnoughInformationCause("data can't be read"))
        else ContentOperation.SetValue(data)
      }
      else null
    }
    return traverseOperationsLogForLookup(
      iterator, direction, condition,
      VfsOperationTagsMask(CONTENT_ACQUIRE_NEW_RECORD, CONTENT_WRITE_BYTES,
                           CONTENT_WRITE_STREAM, CONTENT_WRITE_STREAM_2,
                           CONTENT_REPLACE_BYTES, CONTENT_APPEND_STREAM),
      onValid = { operation, detect ->
        when (operation) {
          is ContentsOperation.AcquireNewRecord -> {
            if (operation.result.hasValue && operation.result.value == contentRecordId) detect(ContentOperation.SetValue(ByteArray(0)))
          }
          is ContentsOperation.WriteBytes -> rewriteContentCase(operation.recordId, operation.dataPayloadRef)?.let(detect)
          is ContentsOperation.WriteStream -> rewriteContentCase(operation.recordId, operation.dataPayloadRef)?.let(detect)
          is ContentsOperation.WriteStream2 -> rewriteContentCase(operation.recordId, operation.dataPayloadRef)?.let(detect)
          is ContentsOperation.ReplaceBytes -> {
            if (operation.recordId == contentRecordId) {
              val data = payloadReader(operation.dataPayloadRef)
              if (data == null) detect(ContentOperation.NotAvailable(NotEnoughInformationCause("data can't be read")))
              else {
                detect(ContentOperation.Modify { before ->
                  if (operation.offset < 0 || operation.offset + data.size > before.size) { // from AbstractStorage.replaceBytes
                    throw VfsRecoveryException("replaceBytes: replace is out of bounds: " +
                                               "offset=${operation.offset} data.size=${data.size} before.size=${before.size}")
                  }
                  else {
                    before.copyOfRange(0, operation.offset) +
                    data +
                    before.copyOfRange(operation.offset + data.size, before.size)
                  }
                })
              }
            }
          }
          is ContentsOperation.AppendStream -> {
            if (operation.recordId == contentRecordId) {
              val data = payloadReader(operation.dataPayloadRef)
              if (data == null) detect(ContentOperation.NotAvailable(NotEnoughInformationCause("data can't be read")))
              else detect(ContentOperation.Modify { before -> before + data })
            }
          }
          else -> throw IllegalStateException("filtered read is broken")
        }
      })
  }

  fun restoreContent(iterator: OperationLogStorage.Iterator,
                     contentRecordId: Int,
                     payloadReader: (PayloadRef) -> ByteArray?,
                     condition: (OperationLogStorage.Iterator) -> Boolean = { true }): State.DefinedState<ByteArray> {
    // TODO: payloadReader should provide diagnostic message on data n/a
    val restoreStack = mutableListOf<ContentOperation.Modify>()
    while (iterator.hasPrevious() && condition(iterator)) {
      val lookup =
        lookupContentOperation(iterator, contentRecordId, payloadReader, TraverseDirection.REWIND, condition)
      if (!lookup.found) return State.notAvailable() // didn't find any relevant content op
      when (val op = lookup.value) {
        is ContentOperation.Modify -> restoreStack.add(op)
        is ContentOperation.NotAvailable -> return State.notAvailable(op.cause)
        is ContentOperation.SetValue -> {
          return restoreStack.foldRight(op.data) { modOp, data ->
            modOp.modify(data)
          }.let(State::ready)
        }
      }
    }
    // no ContentOp.SetValue was found
    return State.notAvailable()
  }

  inline fun traverseOperationsLog(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    crossinline continueCondition: (OperationLogStorage.Iterator) -> Boolean = { true },
    toReadMask: VfsOperationTagsMask,
    onInvalid: (cause: Throwable) -> Unit = { throw it },
    onIncomplete: (tag: VfsOperationTag) -> Unit = {},
    onValid: (operation: VfsOperation<*>) -> Unit
  ) {
    while (iterator.movableIn(direction) && continueCondition(iterator)) {
      when (val read = iterator.moveFiltered(direction, toReadMask)) {
        is OperationReadResult.Invalid -> onInvalid(read.cause)
        is OperationReadResult.Incomplete -> onIncomplete(read.tag)
        is OperationReadResult.Valid -> onValid(read.operation)
      }
    }
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
      ): State.DefinedState<T> = mapCases({ State.notAvailable(notFoundCause()) }, State::ready)
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
      _value = value
      found = true
    }
  }

  /**
   * @param onValid `detect` must be called to succeed the lookup, traversal will stop after it
   */
  inline fun <T> traverseOperationsLogForLookup(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    crossinline continueCondition: (OperationLogStorage.Iterator) -> Boolean = { true },
    toReadMask: VfsOperationTagsMask,
    onInvalid: (cause: Throwable) -> Unit = { throw it },
    onIncomplete: (tag: VfsOperationTag) -> Unit = {},
    onValid: (operation: VfsOperation<*>, detect: (T) -> Unit) -> Unit
  ): LookupResult<T> {
    val result = LookupResultImpl<T>()
    traverseOperationsLog(iterator, direction, { !result.found && continueCondition(it) }, toReadMask, onInvalid, onIncomplete) {
      onValid(it, result::detect)
    }
    return result
  }
}