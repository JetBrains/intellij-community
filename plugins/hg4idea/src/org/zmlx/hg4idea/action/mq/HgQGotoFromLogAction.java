// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action.mq;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.command.mq.HgQGotoCommand;
import org.zmlx.hg4idea.command.mq.HgQPopCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;

public class HgQGotoFromLogAction extends HgMqAppliedPatchAction {

  protected void actionPerformed(final @NotNull HgRepository repository, final @NotNull VcsFullCommitDetails commit) {
    final Project project = repository.getProject();
    List<Hash> parents = commit.getParents();
    final Hash parentHash = parents.isEmpty() ? null : parents.get(0);

    final HgNameWithHashInfo parentPatchName = ContainerUtil.find(repository.getMQAppliedPatches(),
                                                                  info -> info.getHash().equals(parentHash));
    new Task.Backgroundable(repository.getProject(), parentPatchName != null
                                                     ? HgBundle.message("hg4idea.mq.progress.goto", parentPatchName)
                                                     : HgBundle.message("hg4idea.mq.progress.pop")) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (parentPatchName != null) {
          new HgQGotoCommand(repository).executeInCurrentThread(parentPatchName.getName());
        }
        else {
          new HgQPopCommand(repository).executeInCurrentThread();
        }
      }

      @Override
      public void onSuccess() {
        HgShowUnAppliedPatchesAction.showUnAppliedPatches(project, repository);
      }
    }.queue();
  }

  @Override
  protected void actionPerformed(@NotNull HgRepository repository, @NotNull Hash commit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLogCommitSelection selection = e.getRequiredData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);

    selection.requestFullDetails(selectedDetails -> {
      VcsFullCommitDetails fullCommitDetails = ContainerUtil.getFirstItem(selectedDetails);

      assert fullCommitDetails != null;
      final HgRepository repository = getRepositoryForRoot(project, fullCommitDetails.getRoot());
      assert repository != null;

      actionPerformed(repository, fullCommitDetails);
    });
  }
}
