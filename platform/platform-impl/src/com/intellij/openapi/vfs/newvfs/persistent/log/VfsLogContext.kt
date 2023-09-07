// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.SimpleStringPersistentEnumerator
import org.jetbrains.annotations.ApiStatus

interface VfsLogBaseContext {
  val stringEnumerator: DataEnumerator<String>
  fun enumerateAttribute(attribute: FileAttribute): EnumeratedFileAttribute =
    EnumeratedFileAttribute(stringEnumerator.enumerate(attribute.id), attribute.version, attribute.isFixedSize)

  fun deenumerateAttribute(enumeratedAttr: EnumeratedFileAttribute): FileAttribute? {
    return FileAttribute.instantiateForRecovery(
      stringEnumerator.valueOf(enumeratedAttr.enumeratedId) ?: return null,
      enumeratedAttr.version,
      enumeratedAttr.fixedSize
    ) // attribute.shouldEnumerate is not used yet
  }
}

// note: does not need to hold any locks
@ApiStatus.Internal
interface VfsLogOperationTrackingContext : VfsLogBaseContext {
  val payloadWriter: PayloadWriter

  fun trackOperation(tag: VfsOperationTag): OperationTracker

  companion object {
    inline fun <R: Any> VfsLogOperationTrackingContext.trackOperation(
      tag: VfsOperationTag,
      performOperation: OperationTracker.() -> R
    ): R = trackOperation(tag).performOperation()

    fun <R: Any> VfsLogOperationTrackingContext.trackPlainOperation(
      tag: VfsOperationTag,
      composeOperation: (OperationResult<R>) -> VfsOperation<R>,
      performOperation: () -> R
    ): R = trackOperation(tag) {
      performOperation catchResult { result ->
        completeTracking { composeOperation(result) }
      }
    }
  }
}

/**
 * Guarantees that the range of operations from [begin] to [end] will be available until the context
 * is [closed][close]. Context must be closed to let compaction free the storage.
 */
interface VfsLogQueryContext : VfsLogBaseContext, AutoCloseable {
  val payloadReader: PayloadReader

  /**
   * TODO
   * this is [com.intellij.openapi.vfs.newvfs.persistent.log.compaction.CompactedVfsSnapshot] that is positioned at [begin]
   */
  fun getBaseSnapshot(
    getNameByNameId: (Int) -> State.DefinedState<String>,
    getAttributeEnumerator: () -> SimpleStringPersistentEnumerator
  ): ExtendedVfsSnapshot?

  /**
   * @return an Iterator pointing to the start of the available range of operations in [OperationLogStorage],
   *  result is guaranteed to be consistent across multiple invocations (until context is closed).
   *  The resulting Iterator is automatically limited to the available range, i.e. won't be able to go past
   *  [begin] or [end].
   */
  fun begin(): OperationLogStorage.Iterator

  /**
   * @return an Iterator pointing to the end of the available range of operations in [OperationLogStorage],
   *  result is guaranteed to be consistent across multiple invocations (until context is closed).
   *  The resulting Iterator is automatically limited to the available range, i.e. won't be able to go past
   *  [begin] or [end].
   */
  fun end(): OperationLogStorage.Iterator

  /**
   * TODO returns a copy of the context that will inherit current lock, current context will be considered closed after invocation,
   *      but won't release the lock
   */
  fun transferLock(): VfsLogQueryContext
}

@ApiStatus.Internal
interface VfsLogCompactionContext : VfsLogQueryContext, AutoCloseable {
  fun constrainedIterator(position: Long, allowedRangeBegin: Long, allowedRangeEnd: Long): OperationLogStorage.Iterator

  /** produced iterator is unconstrained */
  override fun begin(): OperationLogStorage.Iterator

  /** produced iterator is unconstrained */
  override fun end(): OperationLogStorage.Iterator

  fun cancellationWasRequested(): Boolean

  fun clearOperationLogStorageUpTo(position: Long)
  fun clearPayloadStorageUpTo(position: Long)

  val targetLogSize: Long

  fun getPayloadStorageAdvancePosition(): Long
  fun getPayloadStorageStartOffset(): Long

  override fun getBaseSnapshot(
    getNameByNameId: (Int) -> State.DefinedState<String>,
    getAttributeEnumerator: () -> SimpleStringPersistentEnumerator
  ): Nothing = throw UnsupportedOperationException("we are the very Compaction itself")

  override fun transferLock(): Nothing =
    throw UnsupportedOperationException("compaction context transfer is not permitted")
}

@ApiStatus.Internal
interface VfsLogQueryContextEx : VfsLogQueryContext {
  // TODO refactor this, bad naming
  fun operationLogEmergingSize(): Long
  fun operationLogIterator(position: Long): OperationLogStorage.Iterator
  fun getRecoveryPoints(): List<VfsRecoveryUtils.RecoveryPoint>
}