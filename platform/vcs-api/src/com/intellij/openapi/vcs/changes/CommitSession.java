// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public interface CommitSession {
  /**
   * Marker object for 'default' commit session via {@link com.intellij.openapi.vcs.checkin.CheckinEnvironment}.
   */
  CommitSession VCS_COMMIT = new CommitSession() {
    @Override
    public void execute(@NotNull Collection<? extends Change> changes, @Nullable @NlsSafe String commitMessage) {
    }
  };

  /**
   * Show dialog with additional options before running pre-commit checks.
   *
   * @see com.intellij.openapi.vcs.changes.ui.SessionDialog
   * @see com.intellij.openapi.ui.DialogPanel
   */
  default @Nullable JComponent getAdditionalConfigurationUI(@NotNull Collection<? extends Change> changes, @Nullable @NlsSafe String commitMessage) {
    return null;
  }

  /**
   * Whether OK action is enabled for the {@link #getAdditionalConfigurationUI} dialog.
   */
  default boolean canExecute(Collection<? extends Change> changes, @NlsSafe String commitMessage) {
    return true;
  }

  void execute(@NotNull Collection<? extends Change> changes, @Nullable @NlsSafe String commitMessage);

  /**
   * Called if commit operation was cancelled.
   */
  default void executionCanceled() {
  }

  /**
   * @return the ID of the help topic to show for the {@link #getAdditionalConfigurationUI} dialog.
   */
  default @Nullable @NonNls String getHelpId() {
    return null;
  }

  /**
   * @return fields validation for the {@link #getAdditionalConfigurationUI} dialog.
   */
  @RequiresEdt
  default ValidationInfo validateFields() {
    return null;
  }
}
