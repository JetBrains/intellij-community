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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Receives notifications about changes in the virtual file system, just as {@link BulkFileListener} and {@link VirtualFileListener},
 * but on a pooled thread, which allows to off-load the EDT, but requires more care in the listener code due to asynchronousness and
 * the absence of read action.</p>
 *
 * <p>Use the {@link AsyncVfsEventsPostProcessor#addListener(AsyncVfsEventsListener, Disposable)} to subscribe.</p>
 *
 * @see AsyncVfsEventsPostProcessor
 */
@ApiStatus.Experimental
public interface AsyncVfsEventsListener {

  /**
   * Invoked after the given events were applied to the VFS. <br/><br/>
   *
   * The call happens on a pooled thread, under a special {@link ProgressIndicator} which is cancelled on project disposal,
   * thus one can call {@code ProgressManager.checkCancelled()} to cancel the background task when the project is disposed.
   */
  void filesChanged(@NotNull List<VFileEvent> events);
}
