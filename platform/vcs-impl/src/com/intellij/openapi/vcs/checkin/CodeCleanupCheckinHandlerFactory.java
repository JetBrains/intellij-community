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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public class CodeCleanupCheckinHandlerFactory extends CheckinHandlerFactory  {
  @Override
  @NotNull
  public CheckinHandler createHandler(@NotNull final CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new CleanupCodeCheckinHandler(panel);
  }

  private static class CleanupCodeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {
    private final CheckinProjectPanel myPanel;
    private Project myProject;

    public CleanupCodeCheckinHandler(CheckinProjectPanel panel) {
      myProject = panel.getProject();
      myPanel = panel;
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
      final JCheckBox cleanupCodeCb = new NonFocusableCheckBox(VcsBundle.message("before.checkin.cleanup.code"));
      return new RefreshableOnComponent() {
        @Override
        public JComponent getComponent() {
          final JPanel cbPanel = new JPanel(new BorderLayout());
          cbPanel.add(cleanupCodeCb, BorderLayout.WEST);
          CheckinHandlerUtil
            .disableWhenDumb(myProject, cleanupCodeCb, "Code analysis is impossible until indices are up-to-date");
          return cbPanel;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void saveState() {
          VcsConfiguration.getInstance(myProject).CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT = cleanupCodeCb.isSelected();
        }

        @Override
        public void restoreState() {
          cleanupCodeCb.setSelected(VcsConfiguration.getInstance(myProject).CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT);
        }
      };
    }


    @Override
    public void runCheckinHandlers(@NotNull Runnable runnable) {
      if (VcsConfiguration.getInstance(myProject).CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT  && !DumbService.isDumb(myProject)) {

        List<VirtualFile> filesToProcess = CheckinHandlerUtil.filterOutGeneratedAndExcludedFiles(myPanel.getVirtualFiles(), myProject);
        GlobalInspectionContextBase.codeCleanup(myProject, new AnalysisScope(myProject, filesToProcess), runnable);

      } else {
        runnable.run();
      }
    }
  }
}