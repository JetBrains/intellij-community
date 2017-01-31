/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class ShowGraphHistoryAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
    assert logManager != null;
    VcsLogContentProvider
      .openLogTab(project, logManager, VcsLogContentProvider.TAB_NAME, file.getName(), new VcsLogUiWithFileFilterFactory(logManager, file));
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (!Registry.is("vcs.log.graph.history")) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
      Project project = e.getProject();
      if (file == null || project == null) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        VirtualFile root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file);
        VcsLogData dataManager = VcsProjectLog.getInstance(project).getDataManager();
        if (root == null || dataManager == null) {
          presentation.setEnabledAndVisible(false);
        }
        else {
          presentation.setVisible(dataManager.getRoots().contains(root));
          presentation.setEnabled(dataManager.getIndex().isIndexed(root));
        }
      }
    }
  }

  private static class VcsLogUiWithFileFilterFactory implements VcsLogManager.VcsLogUiFactory<AbstractVcsLogUi> {
    private final VcsLogManager myLogManager;
    private final VirtualFile myFile;

    public VcsLogUiWithFileFilterFactory(@NotNull VcsLogManager logManager, @NotNull VirtualFile file) {
      myLogManager = logManager;
      myFile = file;
    }

    @Override
    public AbstractVcsLogUi createLogUi(@NotNull Project project,
                                        @NotNull VcsLogData logData,
                                        @NotNull VcsLogColorManager colorManager) {
      VcsLogUiImpl ui = myLogManager.getMainLogUiFactory(myFile.getName()).createLogUi(project, logData, colorManager);
      ui.getFilterUi().setFilter(new VcsLogStructureFilterImpl(Collections.singleton(VcsUtil.getFilePath(myFile))));
      return ui;
    }
  }
}