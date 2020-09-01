// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface VcsHistoryProvider {

  VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, final JComponent forShortcutRegistration);

  AnAction[] getAdditionalActions(final Runnable refresher);

  /**
   * Returns whether the history provider submits the custom-formatted date
   * and the standard "Date" column must be omitted to get rid of confusion.
   * @return true if history provider submits the custom-formatted date.
   */
  boolean isDateOmittable();

  @Nullable
  @NonNls
  String getHelpId();

  /**
   * Returns the history session for the specified file path.
   *
   * @param filePath the file path for which the session is requested.
   * @return the session, or null if the initial revisions loading process was cancelled.
   * @throws VcsException if an error occurred when loading the revisions
   */
  @Nullable
  @RequiresBackgroundThread
  VcsHistorySession createSessionFor(FilePath filePath) throws VcsException;

  void reportAppendableHistory(final FilePath path, final VcsAppendableHistorySessionPartner partner) throws VcsException;

  boolean supportsHistoryForDirectories();

  /**
   * The returned {@link DiffFromHistoryHandler} will be called, when user calls "Show Diff" from the file history panel.
   * If {@code null} is returned, the standard handler will be used, which is suitable for most cases.
   */
  @Nullable
  DiffFromHistoryHandler getHistoryDiffHandler();

  /**
   * Provide any additional restrictions for showing history for the given file.
   * Basic restrictions are checked in the TabbedShowHistoryAction.
   * @param file File which history is requested or may be requested.
   * @return true if the VCS can show history for this file.
   */
  boolean canShowHistoryFor(@NotNull VirtualFile file);

}
