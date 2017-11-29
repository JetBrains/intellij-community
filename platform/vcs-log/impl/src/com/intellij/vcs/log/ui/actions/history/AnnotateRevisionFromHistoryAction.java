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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.AnnotateRevisionActionBase;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

public class AnnotateRevisionFromHistoryAction extends FileHistorySingleCommitAction {
  @Override
  protected boolean isEnabled(@NotNull FileHistoryUi ui, @Nullable VcsFullCommitDetails detail, @NotNull AnActionEvent e) {
    VcsKey key = e.getData(VcsDataKeys.VCS);
    if (key == null) return false;

    AbstractVcs vcs = VcsUtil.findVcsByKey(notNull(e.getProject()), key);
    if (vcs == null) return false;
    AnnotationProvider provider = vcs.getAnnotationProvider();
    if (provider == null) return false;

    if (detail != null) {
      VcsFileRevision fileRevision = ui.createRevision(detail);
      return AnnotateRevisionActionBase.isEnabled(vcs, ui.createVcsVirtualFile(fileRevision), fileRevision);
    }

    return true;
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryUi ui,
                               @NotNull VcsFullCommitDetails detail,
                               @NotNull AnActionEvent e) {
    VcsKey vcsKey = e.getRequiredData(VcsDataKeys.VCS);

    VcsFileRevision revision = ui.createRevision(detail);
    VirtualFile vcsVirtualFile = ui.createVcsVirtualFile(detail);

    if (!VcsHistoryUtil.isEmpty(revision) && vcsVirtualFile != null) {
      AnnotateRevisionActionBase.annotate(vcsVirtualFile, revision,
                                          notNull(VcsUtil.findVcsByKey(project, vcsKey)),
                                          null, 0);
    }
  }
}
