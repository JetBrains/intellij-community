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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.vcs.commit.CommitMessageInspectionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CommitDialogSettingsPanel implements ConfigurableUi<VcsConfiguration>, Disposable {
  @NotNull private final Project myProject;
  private JBCheckBox myShowUnversionedFiles;
  private JPanel myMainPanel;
  private CommitMessageInspectionsPanel myInspectionsPanel;

  public CommitDialogSettingsPanel(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void reset(@NotNull VcsConfiguration settings) {
    myShowUnversionedFiles.setSelected(settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT);
    myInspectionsPanel.reset();
  }

  @Override
  public boolean isModified(@NotNull VcsConfiguration settings) {
    return settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT != myShowUnversionedFiles.isSelected() || myInspectionsPanel.isModified();
  }

  @Override
  public void apply(@NotNull VcsConfiguration settings) throws ConfigurationException {
    settings.SHOW_UNVERSIONED_FILES_WHILE_COMMIT = myShowUnversionedFiles.isSelected();
    myInspectionsPanel.apply();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myInspectionsPanel = new CommitMessageInspectionsPanel(myProject);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myInspectionsPanel);
  }
}
