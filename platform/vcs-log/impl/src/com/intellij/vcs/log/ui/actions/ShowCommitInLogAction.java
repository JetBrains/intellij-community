// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

    jumpToRevision(project, HashImpl.build(revision.asString()));
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

  /**
   * Consider using {@link VcsLogNavigationUtil#jumpToRevisionAsync} when the root is known
   */
  @ApiStatus.Internal
  public static void jumpToRevision(@NotNull Project project, @NotNull Hash hash) {
    VcsLogContentUtil.runInMainLog(project, logUi -> jumpToRevisionUnderProgress(project, logUi, hash));
  }

  private static void jumpToRevisionUnderProgress(@NotNull Project project,
                                                  @NotNull VcsLogUiEx logUi,
                                                  @NotNull Hash hash) {
    Future<Boolean> future = VcsLogNavigationUtil.jumpToHash(logUi, hash.asString(), false, true);
    if (!future.isDone()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project,
                                                                VcsLogBundle.message("vcs.log.show.commit.in.log.process", hash.toShortString()),
                                                                false/*can not cancel*/,
                                                                PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            future.get();
          }
          catch (CancellationException | InterruptedException ignored) {
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
      });
    }
  }
}
