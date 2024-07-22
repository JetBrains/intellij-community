// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vfs;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Receives notifications about changes in the virtual file system, just as {@link BulkFileListener} and {@link VirtualFileListener},
 * but on a pooled thread, which allows to off-load the EDT, but requires more care in the listener code due to asynchrony and
 * the absence of read action. For a safer alternative, consider {@link com.intellij.openapi.vfs.AsyncFileListener}.</p>
 *
 * <p>Use the {@link AsyncVfsEventsPostProcessor#addListener(AsyncVfsEventsListener, kotlinx.coroutines.CoroutineScope)} to subscribe.</p>
 *
 * @see AsyncVfsEventsPostProcessor
 */
@ApiStatus.Experimental
public interface AsyncVfsEventsListener {
  /**
   * Invoked after the given events were applied to the VFS. <br/><br/>
   *
   * The call happens on a pooled thread, under a special {@link ProgressIndicator} which is canceled on project disposal,
   * thus one can call {@code ProgressManager.checkCancelled()} to cancel the background task when the project is disposed.
   */
  void filesChanged(@NotNull List<? extends @NotNull VFileEvent> events);
}
