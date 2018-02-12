/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Subscribes to VFS events and processes them further on a dedicated pooled thread to {@link AsyncVfsEventsListener}s. <br/><br/>
 *
 * If your event processing code might be slow (in particular, if it calls {@link VFileEvent#getPath()}), this listener is preferred
 * over original ones. <br/><br/>
 *
 * <b>NB:</b> All listeners are executed on a pooled thread, without read action,
 * so the VFS state is unreliable without additional checks. <br/><br/>
 *
 * @see AsyncVfsEventsListener
 */
@ApiStatus.Experimental
public interface AsyncVfsEventsPostProcessor {

  /**
   * Subscribes the given listener to get the VFS events on a pooled thread.
   * The listener is automatically unsubscribed when the {@code disposable} is disposed.<br/><br/>
   *
   * The caller should properly synchronize the call to {@code addListener()} with the {@code dispose()} of the given Disposable.
   */
  void addListener(@NotNull AsyncVfsEventsListener listener, @NotNull Disposable disposable);

  @NotNull
  static AsyncVfsEventsPostProcessor getInstance() {
    return ServiceManager.getService(AsyncVfsEventsPostProcessor.class);
  }
}
