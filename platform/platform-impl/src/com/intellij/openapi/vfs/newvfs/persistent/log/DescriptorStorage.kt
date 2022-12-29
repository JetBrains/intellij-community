// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

interface DescriptorStorage {
  fun bytesForDescriptor(tag: VfsOperationTag): Int

  /**
   * contract: tag == compute().tag
   */
  suspend fun writeDescriptor(tag: VfsOperationTag, compute: suspend () -> VfsOperation<*>)

  suspend fun readAt(position: Long, action: suspend (VfsOperation<*>?) -> Unit)

  /**
   * @param action return true to continue reading, false to stop.
   */
  suspend fun readAll(action: suspend (VfsOperation<*>) -> Boolean)

  suspend fun serialize(operation: VfsOperation<*>): ByteArray
  suspend fun <T: VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray): T

  fun size(): Long

  fun flush()
  fun dispose()
}