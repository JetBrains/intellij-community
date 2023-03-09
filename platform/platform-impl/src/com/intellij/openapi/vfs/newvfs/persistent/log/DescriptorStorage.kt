// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import kotlinx.coroutines.CoroutineScope

interface DescriptorStorage {
  fun bytesForDescriptor(tag: VfsOperationTag): Int

  /**
   * Allocates space for a descriptor and launches a write operation in [scope]
   * @param compute is called at most once inside the launched coroutine
   * contract: tag == compute().tag
   */
  fun enqueueDescriptorWrite(scope: CoroutineScope, tag: VfsOperationTag, compute: () -> VfsOperation<*>)

  /**
   * Performs an actual descriptor write, not supposed to be called directly.
   * @see enqueueDescriptorWrite
   */
  fun writeDescriptor(position: Long, op: VfsOperation<*>)

  fun readAt(position: Long): DescriptorReadResult

  /**
   * Only reads and deserializes content of operations, that are contained in [toReadMask].
   * If tag is not contained in [toReadMask], then [DescriptorReadResult.Incomplete] is returned.
   * @see readAt
   */
  fun readAtFiltered(position: Long, toReadMask: VfsOperationTagsMask): DescriptorReadResult

  /** Reads a descriptor that precedes the one that starts on [position] */
  fun readPreceding(position: Long): DescriptorReadResult

  /**
   * Tries to read the whole storage in a sequential manner.
   * In case [DescriptorReadResult.Invalid] was read, it will be the last item to be passed to [action].
   * @param action return true to continue reading, false to stop.
   */
  fun readAll(action: (DescriptorReadResult) -> Boolean)

  fun serialize(operation: VfsOperation<*>): ByteArray
  fun <T : VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray): T

  /**
   * Size of storage in bytes. The range [0, size) of storage is guaranteed to contain only descriptors
   * for which their write procedures have been finished already.
   */
  fun size(): Long

  /**
   * Similar to [size], but in addition there is a guarantee that [flush] has happened and before its
   * invocation [size] was at least current [persistentSize].
   */
  fun persistentSize(): Long

  /**
   * A [VfsLogIterator] that is initially positioned at the beginning of the storage.
   */
  fun begin(): VfsLogIterator

  /**
   * A [VfsLogIterator] that is initially positioned at the end of the storage.
   */
  fun end(): VfsLogIterator

  fun flush()
  fun dispose()

  sealed interface DescriptorReadResult {
    /** Descriptor was read correctly */
    data class Valid(val operation: VfsOperation<*>) : DescriptorReadResult

    /** Attempt to read a descriptor has failed but operation tag was recovered */
    data class Incomplete(val tag: VfsOperationTag) : DescriptorReadResult

    /** Couldn't retrieve any information at all */
    data class Invalid(val cause: Throwable) : DescriptorReadResult
  }

  /**
   * [VfsLogIterator] gets invalidated in case [DescriptorReadResult.Invalid] was read, and its [hasNext] and [hasPrevious]
   * will return false afterward in such case.
   */
  interface VfsLogIterator {
    fun hasNext(): Boolean
    fun hasPrevious(): Boolean

    fun next(): DescriptorReadResult
    fun previous(): DescriptorReadResult
  }
}