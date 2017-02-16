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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowAllAffectedFromHistoryAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsKey vcsKey = e.getRequiredData(VcsDataKeys.VCS);
    VcsFileRevision revision = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISION);
    VirtualFile revisionVirtualFile = e.getRequiredData(VcsDataKeys.VCS_VIRTUAL_FILE);
    Boolean isNonLocal = e.getData(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION);

    AbstractVcsHelper.getInstance(project).loadAndShowCommittedChangesDetails(project, revision.getRevisionNumber(), revisionVirtualFile,
                                                                              vcsKey, revision.getChangedRepositoryPath(),
                                                                              Boolean.TRUE.equals(isNonLocal));
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (project == null || vcsKey == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    Boolean isNonLocal = e.getData(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION);
    VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    boolean enabled = (e.getData(VcsDataKeys.VCS_FILE_REVISION) != null) && (revisionVirtualFile != null);
    enabled = enabled && (!Boolean.TRUE.equals(isNonLocal) || canPresentNonLocal(project, vcsKey, revisionVirtualFile));
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean canPresentNonLocal(Project project, VcsKey key, final VirtualFile file) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(key.getName());
    if (vcs == null) return false;
    CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    if (provider == null) return false;
    return provider.getForNonLocal(file) != null;
  }
}
