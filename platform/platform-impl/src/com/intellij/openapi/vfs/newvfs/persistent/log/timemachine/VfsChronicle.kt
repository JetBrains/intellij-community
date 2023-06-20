// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.movableIn
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.moveFiltered
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag.*
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.AttributeDataRule.Companion.forFileId
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.ContentModificationRule.Companion.forContentRecordId
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.ContentOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.PropertyOverwriteRule.Companion.forFileId
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.bind
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
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.nameId, stopIf)

  inline fun lookupParentId(iterator: OperationLogStorage.Iterator,
                            fileId: Int,
                            direction: TraverseDirection = TraverseDirection.REWIND,
                            crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.parentId, stopIf)

  inline fun lookupLength(iterator: OperationLogStorage.Iterator,
                          fileId: Int,
                          direction: TraverseDirection = TraverseDirection.REWIND,
                          crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Long> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.length, stopIf)

  inline fun lookupTimestamp(iterator: OperationLogStorage.Iterator,
                             fileId: Int,
                             direction: TraverseDirection = TraverseDirection.REWIND,
                             crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Long> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.timestamp, stopIf)

  inline fun lookupFlags(iterator: OperationLogStorage.Iterator,
                         fileId: Int,
                         direction: TraverseDirection = TraverseDirection.REWIND,
                         crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.flags, stopIf)

  inline fun lookupContentRecordId(iterator: OperationLogStorage.Iterator,
                                   fileId: Int,
                                   direction: TraverseDirection = TraverseDirection.REWIND,
                                   crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.contentRecordId, stopIf)

  inline fun lookupAttributeRecordId(iterator: OperationLogStorage.Iterator,
                                     fileId: Int,
                                     direction: TraverseDirection = TraverseDirection.REWIND,
                                     crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<Int> =
    traverseOperationsLogForPropertyOverwriteLookup(iterator, direction, fileId, VfsModificationContract.attributeRecordId, stopIf)

  fun lookupContentOperation(iterator: OperationLogStorage.Iterator,
                             contentRecordId: Int,
                             direction: TraverseDirection = TraverseDirection.REWIND,
                             stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<ContentOperation> =
    traverseOperationsLogForVfsModificationLookup(
      iterator, direction,
      VfsModificationContract.content.relevantOperations, VfsModificationContract.content.forContentRecordId(contentRecordId),
      stopIf
    )

  interface ContentRestorationSequence {
    val initial: ContentOperation.Set

    /** in chronological order */
    val modifications: List<ContentOperation.Modify>

    companion object {
      fun ContentRestorationSequence.restoreContent(payloadReader: (PayloadRef) -> State.DefinedState<ByteArray>): State.DefinedState<ByteArray> =
        modifications.fold(initial.readContent(payloadReader)) { data, modOp ->
          data.bind { modOp.modifyContent(it, payloadReader) }
        }
    }
  }

  class ContentRestorationSequenceBuilder {
    private val reversedModifications = mutableListOf<ContentOperation.Modify>()
    private var initial: ContentOperation.Set? = null

    val isFormed: Boolean get() = initial != null

    fun prependModification(mod: ContentOperation.Modify) = reversedModifications.add(mod)

    fun setInitial(set: ContentOperation.Set) {
      assert(initial == null)
      initial = set
    }

    fun buildWithInitial(set: ContentOperation.Set): ContentRestorationSequence =
      ContentRestorationSequenceImpl(set, reversedModifications.reversed())
        .also { assert(initial == null) }

    fun buildIfInitialIsPresent(): ContentRestorationSequence? = initial?.let {
      ContentRestorationSequenceImpl(it, reversedModifications.reversed())
    }

    companion object {
      private class ContentRestorationSequenceImpl(override val initial: ContentOperation.Set,
                                                   override val modifications: List<ContentOperation.Modify>) : ContentRestorationSequence
    }
  }


  fun lookupContentRestorationStack(iterator: OperationLogStorage.Iterator,
                                    contentRecordId: Int,
                                    stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): State.DefinedState<ContentRestorationSequence> {
    if (contentRecordId == 0) return State.NotAvailable(NotEnoughInformationCause("VFS didn't cache file's content"))
    val seqBuilder = ContentRestorationSequenceBuilder()
    while (iterator.hasPrevious() && !stopIf(iterator)) {
      val lookup = lookupContentOperation(iterator, contentRecordId, TraverseDirection.REWIND, stopIf)
      if (!lookup.found) return State.NotAvailable() // didn't find any relevant content op
      when (val op = lookup.value) {
        is ContentOperation.Modify -> seqBuilder.prependModification(op)
        is ContentOperation.Set -> {
          return seqBuilder.buildWithInitial(op).let(State::Ready)
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
                                 enumeratedAttribute: EnumeratedFileAttribute,
                                 direction: TraverseDirection = TraverseDirection.REWIND,
                                 crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false }): LookupResult<PayloadRef?> =
    traverseOperationsLogForVfsModificationLookup(
      iterator, direction,
      VfsModificationContract.attributeData.relevantOperations,
      VfsModificationContract.attributeData.forFileId(fileId).andIf { it.affectsAttribute(enumeratedAttribute) }.map { it.data },
      stopIf
    )

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
            if (fileId == id) return RecoveredChildrenIds.of(childrenIds.toList(), true)
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
    return RecoveredChildrenIds.of(childrenIds.toList(), false)
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
        notFoundCause: () -> NotEnoughInformationCause = { UnspecifiedNotAvailableException }
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

  inline fun <T> traverseOperationsLogForVfsModificationLookup(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    relevantOperations: VfsOperationTagsMask,
    ifModifies: ConditionalVfsModifier<T>,
    crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false },
    onInvalid: TraverseContext.(cause: Throwable) -> Unit = { throw it },
    onIncomplete: TraverseContext.(tag: VfsOperationTag) -> Unit = { if (relevantOperations.contains(it)) stop() },
    onCompleteExceptional: TraverseContext.(operation: VfsOperation<*>, detect: (T) -> Unit) -> Unit = { _, _ -> stop() },
  ): LookupResult<T> =
    traverseOperationsLogForLookup(
      iterator, direction, relevantOperations, stopIf,
      onInvalid, onIncomplete, onCompleteExceptional,
      onComplete = { op, detect -> op.ifModifies(detect) }
    )

  inline fun <T> traverseOperationsLogForPropertyOverwriteLookup(
    iterator: OperationLogStorage.Iterator,
    direction: TraverseDirection,
    fileId: Int,
    propertyRule: VfsModificationContract.PropertyOverwriteRule<T>,
    crossinline stopIf: (OperationLogStorage.Iterator) -> Boolean = { false },
    onInvalid: TraverseContext.(cause: Throwable) -> Unit = { throw it },
    onIncomplete: TraverseContext.(tag: VfsOperationTag) -> Unit = { if (propertyRule.relevantOperations.contains(it)) stop() },
    onCompleteExceptional: TraverseContext.(operation: VfsOperation<*>, detect: (T) -> Unit) -> Unit = { _, _ -> stop() },
  ): LookupResult<T> =
    traverseOperationsLogForVfsModificationLookup(
      iterator, direction,
      propertyRule.relevantOperations, propertyRule.forFileId(fileId), stopIf,
      onInvalid, onIncomplete, onCompleteExceptional
    )
}