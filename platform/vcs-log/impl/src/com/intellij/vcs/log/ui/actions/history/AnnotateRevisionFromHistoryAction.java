// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.history.FileHistoryUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AnnotateRevisionFromHistoryAction extends FileHistoryMetadataAction {

  @Override
  protected boolean isEnabled(@NotNull FileHistoryUi ui, @Nullable VcsCommitMetadata detail, @NotNull AnActionEvent e) {
    VcsKey key = e.getData(VcsDataKeys.VCS);
    if (key == null) return false;

    AbstractVcs vcs = VcsUtil.findVcsByKey(Objects.requireNonNull(e.getProject()), key);
    if (vcs == null) return false;
    AnnotationProvider provider = vcs.getAnnotationProvider();
    if (provider == null) return false;

    if (detail != null) {
      VcsFileRevision fileRevision = ui.createRevision(detail);
      return AnnotateRevisionActionBase.isEnabled(vcs, FileHistoryUtil.createVcsVirtualFile(fileRevision), fileRevision);
    }

    return true;
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryUi ui,
                               @NotNull VcsCommitMetadata detail,
                               @NotNull AnActionEvent e) {
    VcsKey vcsKey = e.getRequiredData(VcsDataKeys.VCS);

    VcsFileRevision revision = ui.createRevision(detail);
    VirtualFile vcsVirtualFile = FileHistoryUtil.createVcsVirtualFile(revision);

    if (!VcsHistoryUtil.isEmpty(revision) && vcsVirtualFile != null) {
      AnnotateRevisionActionBase.annotate(vcsVirtualFile, revision,
                                          Objects.requireNonNull(VcsUtil.findVcsByKey(project, vcsKey)),
                                          null, 0);
    }
  }
}
