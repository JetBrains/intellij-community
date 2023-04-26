// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import kotlinx.coroutines.CoroutineScope

interface OperationLogStorage {
  /**
   * How many bytes takes the [VfsOperation]'s descriptor in a persistent storage.
   */
  fun bytesForOperationDescriptor(tag: VfsOperationTag): Int

  /**
   * Allocates space for an operation's descriptor and launches a write operation in [scope]
   * @param compute is called at most once inside the launched coroutine
   * contract: tag == compute().tag
   */
  fun enqueueOperationWrite(scope: CoroutineScope, tag: VfsOperationTag, compute: () -> VfsOperation<*>)

  /**
   * Performs an actual operation write, not supposed to be called directly.
   * @see enqueueOperationWrite
   */
  fun writeOperation(position: Long, op: VfsOperation<*>)

  fun readAt(position: Long): OperationReadResult

  /**
   * Only reads and deserializes content of operations, that are contained in [toReadMask].
   * If tag is not contained in [toReadMask], then [OperationReadResult.Incomplete] is returned.
   * @see readAt
   */
  fun readAtFiltered(position: Long, toReadMask: VfsOperationTagsMask): OperationReadResult

  /** Reads an operation that precedes the one that starts on [position] */
  fun readPreceding(position: Long): OperationReadResult

  /**
   * @see readAtFiltered
   * @see readPreceding
   */
  fun readPrecedingFiltered(position: Long, toReadMask: VfsOperationTagsMask): OperationReadResult

  /**
   * Tries to read the whole storage in a sequential manner.
   * In case [OperationReadResult.Invalid] was read, it will be the last item to be passed to [action].
   * @param action return true to continue reading, false to stop.
   */
  fun readAll(action: (OperationReadResult) -> Boolean)

  /**
   * Size of storage in bytes. The range [0, size) of storage is guaranteed to contain only operations
   * for which their write procedures have been finished already.
   * The following holds: [persistentSize] <= [size] <= [emergingSize]
   * @see [persistentSize]
   * @see [emergingSize]
   */
  fun size(): Long

  /**
   * Similar to [size], but the range [0, emergingSize) may contain operations for which their write
   * procedures are not finished yet (but space is already allocated).
   */
  fun emergingSize(): Long

  /**
   * Similar to [size], but in addition there is a guarantee that [flush] has happened and before its
   * invocation [size] was at least current [persistentSize].
   */
  fun persistentSize(): Long

  /**
   * A [Iterator] that is initially positioned at the beginning of the storage.
   */
  fun begin(): Iterator

  /**
   * A [Iterator] that is initially positioned at the end of the storage.
   */
  fun end(): Iterator

  fun flush()
  fun dispose()

  sealed interface OperationReadResult {
    /** Operation was read correctly */
    data class Valid(val operation: VfsOperation<*>) : OperationReadResult

    /** Attempt to read an operation has failed but operation tag was recovered */
    data class Incomplete(val tag: VfsOperationTag) : OperationReadResult

    /** Couldn't retrieve any information at all */
    data class Invalid(val cause: Throwable) : OperationReadResult

    companion object {
      fun OperationReadResult.getTag(): VfsOperationTag = when (this) {
        is Valid -> operation.tag
        is Incomplete -> tag
        is Invalid -> throw IllegalAccessException("data access on OperationReadResult.Invalid")
      }
    }
  }

  /**
   * [Iterator] gets invalidated in case [OperationReadResult.Invalid] was read, and its [hasNext] and [hasPrevious]
   * will return false afterward in such case.
   *
   * Comparison is performed in terms of relative position, e.g. `iter1 < iter2` means that `iter1` is positioned strictly before `iter2`
   */
  interface Iterator: Comparable<Iterator>, BiDiIterator<OperationReadResult> {
    /**
     * Creates a complete and independent copy of an iterator.
     */
    fun copy(): Iterator

    /**
     * @see [OperationLogStorage.readAtFiltered]
     */
    fun nextFiltered(mask: VfsOperationTagsMask): OperationReadResult

    /**
     * @see [OperationLogStorage.readAtFiltered]
     */
    fun previousFiltered(mask: VfsOperationTagsMask): OperationReadResult
  }
}