// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

/**
 * Subscribes to VFS events and processes them further on a dedicated pooled thread to {@link AsyncVfsEventsListener}s. <br/><br/>
 *
 * If your event processing code might be slow (in particular, if it calls {@link VFileEvent#getPath()}), this listener is preferred
 * over original ones. Please also consider a safer {@link com.intellij.openapi.vfs.AsyncFileListener}.<br/><br/>
 *
 * <b>NB:</b> All listeners are executed on a pooled thread, without read action,
 * so the VFS state is unreliable without additional checks. <br/><br/>
 *
 * @see AsyncVfsEventsListener
 */
public interface AsyncVfsEventsPostProcessor {
  /**
   * Subscribes the given listener to get the VFS events on a pooled thread.
   * The listener is automatically unsubscribed when the {@code disposable} is disposed.<br/><br/>
   *
   * The caller should properly synchronize the call to {@code addListener()} with the {@code dispose()} of the given Disposable.
   */
  void addListener(@NotNull AsyncVfsEventsListener listener, @NotNull CoroutineScope coroutineScope);

  @NotNull
  static AsyncVfsEventsPostProcessor getInstance() {
    return ApplicationManager.getApplication().getService(AsyncVfsEventsPostProcessor.class);
  }
}
