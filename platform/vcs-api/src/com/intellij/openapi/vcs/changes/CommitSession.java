// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public interface CommitSession {
  CommitSession VCS_COMMIT = new CommitSession() {
    @Override
    public void execute(@NotNull Collection<Change> changes, @Nullable String commitMessage) {
    }
  };

  /**
   * @deprecated Since version 7.0, {@link #getAdditionalConfigurationUI(Collection, String)} is called instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @Nullable
  default JComponent getAdditionalConfigurationUI() {
    return null;
  }

  @Nullable
  default JComponent getAdditionalConfigurationUI(@NotNull Collection<Change> changes, @Nullable String commitMessage) {
    return getAdditionalConfigurationUI();
  }

  default boolean canExecute(Collection<Change> changes, String commitMessage) {
    return true;
  }

  void execute(@NotNull Collection<Change> changes, @Nullable String commitMessage);

  default void executionCanceled() {
  }

  /**
   * @return the ID of the help topic to show for the dialog
   */
  @Nullable
  default String getHelpId() {
    return null;
  }

  @CalledInAwt
  default ValidationInfo validateFields() {
    return null;
  }
}
