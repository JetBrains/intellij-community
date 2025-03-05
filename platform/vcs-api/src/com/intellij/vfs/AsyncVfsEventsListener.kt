// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vfs

import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Receives notifications about changes in the virtual file system,
 * just as [com.intellij.openapi.vfs.newvfs.BulkFileListener] and [com.intellij.openapi.vfs.VirtualFileListener],
 * but on a pooled thread, which allows to off-load the EDT, but requires more care in the listener code due to asynchrony and
 * the absence of read action.
 * For a safer alternative, consider [com.intellij.openapi.vfs.AsyncFileListener].
 *
 * Use the [AsyncVfsEventsPostProcessor.addListener] to subscribe.
 *
 * @see AsyncVfsEventsPostProcessor
 */
interface AsyncVfsEventsListener {
  /**
   * Invoked after the given events were applied to the VFS.
   */
  suspend fun filesChanged(events: List<VFileEvent>)
}
