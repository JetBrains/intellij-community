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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.AnnotateRevisionActionBase;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.notNull;

public class AnnotateRevisionFromHistoryAction extends FileHistorySingleCommitAction {
  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    VcsFileRevision fileRevision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (fileRevision == null) return false;

    VirtualFile file = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (file == null) return false;

    VcsKey key = e.getData(VcsDataKeys.VCS);
    if (key == null) return false;

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(e.getProject()).findVcsByName(key.getName());
    if (vcs == null) return false;

    return AnnotateRevisionActionBase.isEnabled(vcs, file, fileRevision);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsKey key = e.getRequiredData(VcsDataKeys.VCS);

    AnnotateRevisionActionBase.annotate(e.getRequiredData(VcsDataKeys.VCS_VIRTUAL_FILE),
                                        e.getRequiredData(VcsDataKeys.VCS_FILE_REVISION),
                                        notNull(ProjectLevelVcsManager.getInstance(project).findVcsByName(key.getName())),
                                        null, 0);
  }
}
