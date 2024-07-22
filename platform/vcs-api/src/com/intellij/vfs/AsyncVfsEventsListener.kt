// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vfs

import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus

/**
 *
 * Receives notifications about changes in the virtual file system, just as [BulkFileListener] and [VirtualFileListener],
 * but on a pooled thread, which allows to off-load the EDT, but requires more care in the listener code due to asynchrony and
 * the absence of read action. For a safer alternative, consider [com.intellij.openapi.vfs.AsyncFileListener].
 *
 *
 * Use the [AsyncVfsEventsPostProcessor.addListener] to subscribe.
 *
 * @see AsyncVfsEventsPostProcessor
 */
@ApiStatus.Experimental
interface AsyncVfsEventsListener {
  /**
   * Invoked after the given events were applied to the VFS. <br></br><br></br>
   *
   * The call happens on a pooled thread, under a special [ProgressIndicator] which is canceled on project disposal,
   * thus one can call `ProgressManager.checkCancelled()` to cancel the background task when the project is disposed.
   */
  fun filesChanged(events: List<VFileEvent>)
}
