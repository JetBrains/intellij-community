// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.movableIn
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.moveFiltered
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag.*
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTagsMask
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State

object VfsChronicle {

  /*
   * lookup... methods traverse operations log in a given direction and return a filled LookupResult if evidence was found
   * that a property was set to a contained value in that range (first such event in traversal order)
   */

  // TODO: lookup methods can throw on Invalid, it should be caught and processed in usages

  inline fun lookupNameId(iterator: OperationLogStorage.Iterator,
                          fileId: Int,
                          direction: TraverseDirection = TraverseDirection.REWIND,
                          crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }) =
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
                            crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }) =
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
      val read = iterator.moveFiltered(direction, toReadMask)
      when (read) {
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
      inline fun <T, R> LookupResult<T>.mapCases(onNotFound: () -> R, onFound: (value: T) -> R): R = if (found) onFound(value) else onNotFound()
      fun <T> LookupResult<T>.getOrNull(): T? = mapCases({ null }) { it }

      inline fun <T> LookupResult<T>.toState(
        notFoundCause: () -> Throwable = { State.Companion.UnspecifiedNotAvailableCause }
      ): State.DefinedState<T> = mapCases({ State.notAvailable(notFoundCause()) }, State::ready)
    }
  }

  class LookupResultImpl<T> : LookupResult<T> {
    override var found: Boolean = false
    private var value_: T? = null

    @Suppress("UNCHECKED_CAST")
    override val value: T
      get() = if (found) {
        value_ as T
      }
      else {
        throw IllegalStateException("Lookup didn't succeed")
      }

    fun detect(value: T) {
      value_ = value
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