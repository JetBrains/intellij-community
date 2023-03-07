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

  fun readAt(position: Long, action: (VfsOperation<*>?) -> Unit)

  /**
   * @param action return true to continue reading, false to stop.
   */
  fun readAll(action: (VfsOperation<*>) -> Boolean)

  fun serialize(operation: VfsOperation<*>): ByteArray
  fun <T: VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray): T

  fun size(): Long

  fun flush()
  fun dispose()
}