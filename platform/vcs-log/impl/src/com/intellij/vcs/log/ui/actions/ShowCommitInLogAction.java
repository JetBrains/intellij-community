// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class ShowCommitInLogAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(ShowCommitInLogAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsRevisionNumber revision = getRevisionNumber(event);
    if (revision == null) return;

    VcsLogCommitSelection commitSelection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    List<CommitId> commits = commitSelection != null ? commitSelection.getCommits() : Collections.emptyList();
    if (!commits.isEmpty()) {
      CommitId commitId = ContainerUtil.getFirstItem(commits);
      VcsLogNavigationUtil.jumpToRevisionAsync(project, commitId.getRoot(), commitId.getHash(), null);
      return;
    }

    VcsProjectLog.showRevisionInMainLog(project, HashImpl.build(revision.asString()));
  }

  protected @Nullable VcsRevisionNumber getRevisionNumber(@NotNull AnActionEvent event) {
    VcsRevisionNumber revision = event.getData(VcsDataKeys.VCS_REVISION_NUMBER);
    if (revision == null) {
      VcsFileRevision fileRevision = event.getData(VcsDataKeys.VCS_FILE_REVISION);
      if (fileRevision != null) {
        revision = fileRevision.getRevisionNumber();
      }
    }
    return revision;
  }

  protected @Nullable VcsKey getVcsKey(@NotNull AnActionEvent event) {
    return event.getData(VcsDataKeys.VCS);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsKey vcsKey = getVcsKey(e);
    e.getPresentation().setEnabled(project != null && getRevisionNumber(e) != null && vcsKey != null
                                   && VcsProjectLog.getSupportedVcs(project).contains(vcsKey));
    e.getPresentation().setText(getActionText(project, vcsKey));
  }

  private static @NlsActions.ActionText @NotNull String getActionText(@Nullable Project project, @Nullable VcsKey vcsKey) {
    if (project != null && vcsKey != null) {
      AbstractVcs vcs = VcsUtil.findVcsByKey(project, vcsKey);
      if (vcs != null) return VcsLogBundle.message("action.Vcs.Log.SelectInLog.text.template", vcs.getDisplayName());
    }
    return VcsLogBundle.message("action.Vcs.Log.SelectInLog.text");
  }
}
