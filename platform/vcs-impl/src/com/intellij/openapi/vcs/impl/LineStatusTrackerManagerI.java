// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
