// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.util.io.DataEnumerator
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
interface VfsLogOperationWriteContext : VfsLogBaseContext {
  val payloadWriter: PayloadWriter
  fun enqueueOperationWrite(tag: VfsOperationTag, compute: VfsLogOperationWriteContext.() -> VfsOperation<*>)
}

/**
 * Guarantees that the range of operations from [begin] to [end] will be available until the context
 * is [closed][close]. Context must be closed to let compaction free the storage.
 */
interface VfsLogQueryContext : VfsLogBaseContext, AutoCloseable {
  val payloadReader: PayloadReader
  // TODO access to compacted vfs instance

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
}

@ApiStatus.Internal
interface VfsLogCompactionContext : VfsLogBaseContext, AutoCloseable {
  // TODO
}