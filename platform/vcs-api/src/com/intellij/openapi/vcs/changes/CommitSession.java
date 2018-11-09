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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public interface CommitSession {
  CommitSession VCS_COMMIT = new CommitSession() {
    @Override
    public void execute(Collection<Change> changes, String commitMessage) {
    }
  };

  /**
   * @deprecated Since version 7.0, {@link #getAdditionalConfigurationUI(java.util.Collection, String)} is called instead
   */
  @Deprecated
  @Nullable
  default JComponent getAdditionalConfigurationUI() {
    return null;
  }

  @Nullable
  default JComponent getAdditionalConfigurationUI(Collection<Change> changes, String commitMessage) {
    return null;
  }

  default boolean canExecute(Collection<Change> changes, String commitMessage) {
    return true;
  }

  void execute(Collection<Change> changes, String commitMessage);

  default void executionCanceled() {
  }

  /**
   * @return the ID of the help topic to show for the dialog
   * @since 10.5
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
