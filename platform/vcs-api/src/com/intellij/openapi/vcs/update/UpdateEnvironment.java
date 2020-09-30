// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Implemented by a VCS integration to support "Update" and optionally "Integrate" and "Check Status"
 * operations for project, directories or files.
 *
 * @see com.intellij.openapi.vcs.AbstractVcs#getUpdateEnvironment()
 * @see com.intellij.openapi.vcs.AbstractVcs#getIntegrateEnvironment()
 * @see com.intellij.openapi.vcs.AbstractVcs#getStatusEnvironment()
 */
public interface UpdateEnvironment {
  /**
   * Called before the update operation to register file status groups in addition to standard
   * file status groups registered in {@link UpdatedFiles#create}. The implementation can be left
   * empty if the VCS doesn't support any non-standard file statuses.
   *
   * @param updatedFiles the holder for the results of the update/integrate/status operation.
   */
  void fillGroups(UpdatedFiles updatedFiles);

  /**
   * Performs the update/integrate/status operation.
   *
   * @param contentRoots      the content roots for which update/integrate/status was requested by the user.
   * @param updatedFiles      the holder for the results of the update/integrate/status operation.
   * @param progressIndicator the indicator that can be used to report the progress of the operation.
   * @param context           in-out parameter: a link between several sequential update operations (that can be triggered by one update action)
   * @return the update session instance, which can be used to get information about errors that have occurred
   *         during the operation and to perform additional post-update processing.
   * @throws ProcessCanceledException if the update operation has been cancelled by the user. Alternatively,
   *                                  cancellation can be reported by returning true from
   *                                  {@link UpdateSession#isCanceled}.
   */
  @NotNull
  UpdateSession updateDirectories(FilePath @NotNull [] contentRoots, UpdatedFiles updatedFiles,
                                  ProgressIndicator progressIndicator, @NotNull final Ref<SequentialUpdatesContext> context) throws ProcessCanceledException;

  /**
   * Allows to show a settings dialog for the operation.
   *
   * @param files the content roots for which update/integrate/status will be performed.
   * @return the settings dialog instance, or null if the VCS doesn't provide a settings dialog for this operation.
   */
  @Nullable
  Configurable createConfigurable(Collection<FilePath> files);

  boolean validateOptions(final Collection<FilePath> roots);

  /**
   * Returns true if the {@link UpdateSession} created by this UpdateEnvironment will
   * {@link UpdateSession#showNotification() show a custom notification} instead of the standard one.
   */
  @RequiresEdt
  default boolean hasCustomNotification() {
    return false;
  }
}
