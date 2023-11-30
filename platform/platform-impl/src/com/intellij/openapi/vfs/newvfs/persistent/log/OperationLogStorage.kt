// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

interface OperationLogStorage {
  /**
   * How many bytes takes the [VfsOperation]'s descriptor in a persistent storage.
   */
  fun bytesForOperationDescriptor(tag: VfsOperationTag): Int

  /**
   * [completeTracking] must be called exactly once
   */
  interface OperationTracker {
    /**
     * @param trackingCompletedCallback called when operation's descriptor writing is finished, i.e. descriptor can be
     * read from the storage (given there are no pending preceding operations that need tracking completion)
     */
    fun completeTracking(trackingCompletedCallback: (() -> Unit)? = null, composeOperation: () -> VfsOperation<*>)
  }

  fun trackOperation(tag: VfsOperationTag): OperationTracker

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
   * Size of storage in bytes. The range [startOffset, size) of storage is guaranteed to contain only operations
   * for which their write procedures have been finished already.
   * The following holds: [persistentSize] <= [size] <= [emergingSize]
   * @see [persistentSize]
   * @see [emergingSize]
   * @see [startOffset]
   */
  fun size(): Long

  /**
   * Similar to [size], but the range [startOffset, emergingSize) may contain operations for which their write
   * procedures are not finished yet (but space is already allocated).
   */
  fun emergingSize(): Long

  /**
   * Similar to [size], but in addition there is a guarantee that [flush] has happened and before its
   * invocation [size] was at least current [persistentSize].
   */
  fun persistentSize(): Long

  /**
   * Position of the first available byte. There is a guarantee that [startOffset] points to a location where an operation starts
   * (given [startOffset] < [size]).
   */
  fun startOffset(): Long


  /**
   * An [Iterator] that is initially positioned at the beginning of the storage.
   */
  fun begin(): Iterator

  /**
   * An [Iterator] that is initially positioned at the end of the storage.
   */
  fun end(): Iterator

  /**
   * An [Iterator] that is initially positioned at the specified location. [position] must point to an operation start.
   */
  fun iterator(position: Long): Iterator

  fun flush()
  fun dispose()

  sealed interface OperationReadResult {
    /** Operation read succeeded */
    data class Complete(val operation: VfsOperation<*>) : OperationReadResult

    /** Attempt to read an operation has failed but the operation tag was recovered. May happen when the operation has actually completed,
     * but VfsLog failed to write its descriptor to storage, or when VfsLog was disposed before it has managed to write down everything it
     * needed.
     *
     * Also, can be a result of [readAtFiltered], [readPrecedingFiltered].
     */
    data class Incomplete(val tag: VfsOperationTag) : OperationReadResult

    /** Failed to perform a read, because of the unexpected exception or because [OperationLogStorage] is in inconsistent state */
    data class Invalid(val cause: Throwable) : OperationReadResult

    companion object {
      fun OperationReadResult.getTag(): VfsOperationTag = when (this) {
        is Complete -> operation.tag
        is Incomplete -> tag
        is Invalid -> throw IllegalAccessException("data access on OperationReadResult.Invalid")
      }

      inline fun OperationReadResult.onInvalid(body: (cause: Throwable) -> Nothing): OperationReadResult = this.also {
        if (this is Invalid) body(cause)
      }
    }
  }

  /**
   * [Iterator] gets invalidated in case [OperationReadResult.Invalid] was read, and its [hasNext] and [hasPrevious]
   * will return false afterward in such case.
   */
  interface Iterator : BiDiIterator<OperationReadResult> {
    fun getPosition(): Long

    /**
     * Creates a complete copy of the iterator.
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

  enum class TraverseDirection {
    /** absolute position decreases */
    REWIND,

    /** absolute position increases */
    PLAY
  }
}