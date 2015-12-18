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
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.FilePathChangesTreeList;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class UndoApplyPatchDialog extends DialogWrapper {


  private final List<FilePath> myFailedFilePaths;
  private final Label myBeforeLabel;
  private final Project myProject;

  private UndoApplyPatchDialog(@NotNull Project project, @NotNull List<FilePath> filePaths, @NotNull Label beforeLabel) {
    super(project, true);
    myProject = project;
    setTitle("Patch Applying Partly Failed");
    setOKButtonText("Rollback");
    myFailedFilePaths = filePaths;
    myBeforeLabel = beforeLabel;
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    int numFiles = myFailedFilePaths.size();
    String detailedText = numFiles == 0 ? "" : String.format("Failed to apply %s below. ", StringUtil.pluralize("file", numFiles));
    final JLabel infoLabel = new JBLabel(detailedText + "Would you like to rollback all applied?");
    panel.add(infoLabel, BorderLayout.NORTH);
    if (numFiles > 0) {
      FilePathChangesTreeList browser = new FilePathChangesTreeList(myProject, myFailedFilePaths, false, false, null, null) {
        @Override
        public Dimension getPreferredSize() {
          return new Dimension(infoLabel.getPreferredSize().width, 50);
        }
      };
      browser.setChangesToDisplay(myFailedFilePaths);
      panel.add(browser, BorderLayout.CENTER);
    }
    return panel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    LocalHistory.getInstance().revertToLabel(myProject, myProject.getBaseDir(), myBeforeLabel);
  }

  static void rollbackApplyPatch(@NotNull Project project, @NotNull List<FilePath> filePaths,
                                 @NotNull Label historyLabel) {
    new UndoApplyPatchDialog(project, filePaths, historyLabel).show();
  }
}
