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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeImpl;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class UndoApplyPatchDialog extends DialogWrapper {


  private final List<? extends FilePath> myFailedFilePaths;
  private final Project myProject;
  private final boolean myShouldInformAboutBinaries;

  UndoApplyPatchDialog(@NotNull Project project,
                       @NotNull List<? extends FilePath> filePaths,
                       boolean shouldInformAboutBinaries) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("patch.apply.partly.failed.title"));
    setOKButtonText(VcsBundle.message("patch.apply.rollback.action"));
    myFailedFilePaths = filePaths;
    myShouldInformAboutBinaries = shouldInformAboutBinaries;
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    int numFiles = myFailedFilePaths.size();
    JPanel labelsPanel = new JPanel(new BorderLayout());
    final JLabel infoLabel = new JBLabel(XmlStringUtil.wrapInHtml(VcsBundle.message("patch.apply.rollback.prompt", numFiles)));
    labelsPanel.add(infoLabel, BorderLayout.NORTH);
    if (myShouldInformAboutBinaries) {
      JLabel warningLabel = new JLabel(VcsBundle.message("patch.apply.rollback.will.not.affect.binaries.info"));
      warningLabel.setIcon(UIUtil.getBalloonWarningIcon());
      labelsPanel.add(warningLabel, BorderLayout.CENTER);
    }
    panel.add(labelsPanel, BorderLayout.NORTH);
    if (numFiles > 0) {
      ChangesTree browser = new ChangesTreeImpl.FilePaths(myProject, false, false, myFailedFilePaths) {
        @Override
        public Dimension getPreferredSize() {
          return new Dimension(infoLabel.getPreferredSize().width, 50);
        }
      };
      panel.add(ScrollPaneFactory.createScrollPane(browser), BorderLayout.CENTER);
    }
    return panel;
  }
}
