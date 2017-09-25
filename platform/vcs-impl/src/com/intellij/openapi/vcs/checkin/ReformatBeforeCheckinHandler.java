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

package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class ReformatBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
  protected final Project myProject;
  private final CheckinProjectPanel myPanel;

  public ReformatBeforeCheckinHandler(final Project project, final CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox reformatBox = new NonFocusableCheckBox(VcsBundle.message("checkbox.checkin.options.reformat.code"));
    CheckinHandlerUtil.disableWhenDumb(myProject, reformatBox, "Impossible until indices are up-to-date");
    return new RefreshableOnComponent() {
      @Override
      public JComponent getComponent() {
        final JPanel panel = new JPanel(new GridLayout(1, 0));
        panel.add(reformatBox);
        return panel;
      }

      @Override
      public void refresh() {
      }

      @Override
      public void saveState() {
        getSettings().REFORMAT_BEFORE_PROJECT_COMMIT = reformatBox.isSelected();
      }

      @Override
      public void restoreState() {
        reformatBox.setSelected(getSettings().REFORMAT_BEFORE_PROJECT_COMMIT);
      }
    };

  }

  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  @Override
  public void runCheckinHandlers(@NotNull final Runnable finishAction) {
    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    final Collection<VirtualFile> files = myPanel.getVirtualFiles();

    final Runnable performCheckoutAction = () -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      finishAction.run();
    };

    if (configuration.REFORMAT_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      new ReformatCodeProcessor(
        myProject, CheckinHandlerUtil.getPsiFiles(myProject, files), FormatterUtil.REFORMAT_BEFORE_COMMIT_COMMAND_NAME, performCheckoutAction, true
      ).run();
    }
    else {
      performCheckoutAction.run();
    }

  }
}
