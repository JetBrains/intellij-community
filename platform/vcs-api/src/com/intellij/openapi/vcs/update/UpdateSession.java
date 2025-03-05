// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A single update/integrate/status session.
 *
 * @see UpdateEnvironment#updateDirectories
 * @see UpdateSessionAdapter
 */
public interface UpdateSession {
  /**
   * Returns the list of exception objects representing the errors occurred during the update/integrate/status
   * operation, or an empty list if no errors have occurred.
   *
   * @return the list of errors.
   */
  @NotNull
  List<VcsException> getExceptions();

  /**
   * Called when the VFS refresh of the files affected by an update/integrate operation is complete. Can be used,
   * for example, to show a merge dialog for files which have been merged with conflicts.
   */
  void onRefreshFilesCompleted();

  /**
   * Checks if the update/integrate/status information was cancelled by the user.
   *
   * @return true if the operation was cancelled, false otherwise.
   */
  boolean isCanceled();

  /**
   * Returns additional information which should be displayed in the post-update notification, or {@code null}.
   * <p>
   * May contain HTML markup.
   */
  default @Nullable String getAdditionalNotificationContent() {
    return null;
  }

  /**
   * Show notification with results of this UpdateSession, instead of the common standard notification and the standard file tree.
   * @see UpdateEnvironment#hasCustomNotification()
   */
  @RequiresEdt
  default void showNotification() {
  }
}
