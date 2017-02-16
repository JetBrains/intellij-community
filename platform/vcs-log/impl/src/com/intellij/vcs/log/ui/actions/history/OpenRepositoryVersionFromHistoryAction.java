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
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction.hasValidChanges;
import static com.intellij.openapi.vcs.changes.actions.OpenRepositoryVersionAction.openRepositoryVersion;

public class OpenRepositoryVersionFromHistoryAction extends FileHistorySingleCommitAction {

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    Change[] changes = e.getRequiredData(VcsDataKeys.SELECTED_CHANGES);
    openRepositoryVersion(project, changes);
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES);
    return changes != null && hasValidChanges(changes);
  }
}
