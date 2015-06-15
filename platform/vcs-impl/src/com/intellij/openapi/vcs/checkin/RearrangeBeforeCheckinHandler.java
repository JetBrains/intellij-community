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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.RearrangeCodeProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RearrangeBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
  public static final String COMMAND_NAME = CodeInsightBundle.message("process.rearrange.code.before.commit");

  private final Project myProject;
  private final CheckinProjectPanel myPanel;

  public RearrangeBeforeCheckinHandler(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox rearrangeBox = new NonFocusableCheckBox(VcsBundle.message("checkbox.checkin.options.rearrange.code"));
    CheckinHandlerUtil.disableWhenDumb(myProject, rearrangeBox, "Impossible until indices are up-to-date");
    return new RefreshableOnComponent() {
      @Override
      public JComponent getComponent() {
        final JPanel panel = new JPanel(new GridLayout(1, 0));
        panel.add(rearrangeBox);
        return panel;
      }

      @Override
      public void refresh() {
      }

      @Override
      public void saveState() {
        getSettings().REARRANGE_BEFORE_PROJECT_COMMIT = rearrangeBox.isSelected();
      }

      @Override
      public void restoreState() {
        rearrangeBox.setSelected(getSettings().REARRANGE_BEFORE_PROJECT_COMMIT);
      }
    };
  }

  private VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  @Override
  public void runCheckinHandlers(@NotNull final Runnable finishAction) {
    final Runnable performCheckoutAction = new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        finishAction.run();
      }
    };

    if (VcsConfiguration.getInstance(myProject).REARRANGE_BEFORE_PROJECT_COMMIT && !DumbService.isDumb(myProject)) {
      new RearrangeCodeProcessor(
        myProject, CheckinHandlerUtil.getPsiFiles(myProject, myPanel.getVirtualFiles()), COMMAND_NAME, performCheckoutAction
      ).run();
    }
    else {
      performCheckoutAction.run();
    }
  }
}
