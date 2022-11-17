/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The service that tracks locally changed lines in a {@link Document} using VCS.
 *
 * @see LocalLineStatusTrackerProvider
 * @see VcsBaseContentProvider
 * @see UpToDateLineNumberProviderImpl
 * @see LineStatusTrackerSettingListener
 */
public interface LineStatusTrackerManagerI {
  /**
   * @see com.intellij.openapi.vcs.ex.LocalLineStatusTracker
   * @see com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
   */
  @Nullable
  LineStatusTracker<?> getLineStatusTracker(@NotNull Document document);

  @Nullable
  LineStatusTracker<?> getLineStatusTracker(@NotNull VirtualFile file);

  /**
   * By default, trackers are created only for files with an opened {@link com.intellij.openapi.editor.Editor}.
   * This method can be used to explicitly request a tracker to be created.
   * <p>
   * Callers MUST call {@link #releaseTrackerFor} when the tracker is no longer needed.
   *
   * @param requester Marker-object to blame those who did not call {@link #releaseTrackerFor}.
   * @see LineStatusTrackerManager#addTrackerListener(LineStatusTrackerManager.Listener, Disposable)
   */
  @RequiresEdt
  void requestTrackerFor(@NotNull Document document, @NotNull Object requester);

  @RequiresEdt
  void releaseTrackerFor(@NotNull Document document, @NotNull Object requester);


  /**
   * Whether {@link com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker} can be created.
   */
  boolean arePartialChangelistsEnabled();

  boolean arePartialChangelistsEnabled(@NotNull VirtualFile virtualFile);

  /**
   * Invoke callback on EDT when none of the trackers has pending updates scheduled
   * ({@link LineStatusTracker#isOperational()} is up-to-date).
   */
  void invokeAfterUpdate(@NotNull Runnable task);
}
