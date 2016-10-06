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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex;
import com.intellij.vcs.log.impl.VcsProjectLog;

import java.util.List;

public class PrintIndexInfoAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (!Registry.is("vcs.log.highlight.not.indexed")) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      Project project = e.getProject();
      VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
      if (project == null || log == null) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        VcsLogData dataManager = VcsProjectLog.getInstance(project).getDataManager();
        if (dataManager == null) {
          presentation.setEnabledAndVisible(false);
        }
        else {
          VcsLogIndex index = dataManager.getIndex();
          if (!(index instanceof VcsLogPersistentIndex)) {
            presentation.setEnabledAndVisible(false);
          }
          else {
            presentation.setEnabledAndVisible(!log.getSelectedCommits().isEmpty());
          }
        }
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) {
      return;
    }
    VcsLogData dataManager = VcsProjectLog.getInstance(project).getDataManager();
    if (dataManager == null) return;
    VcsLogIndex index = dataManager.getIndex();
    if (!(index instanceof VcsLogPersistentIndex)) return;

    List<CommitId> commits = log.getSelectedCommits();
    for (CommitId commit : commits) {
      ((VcsLogPersistentIndex)index).printDebugInfoForCommit(commit);
    }
    VcsBalloonProblemNotifier.showOverChangesView(project, "Index information for " +
                                                           commits.size() + " " +
                                                           StringUtil.pluralize("commit", commits.size()) +
                                                           " was written into log file", MessageType.INFO);
  }
}
