// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.branch.HgBranchPopupActions;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collections;

import static org.zmlx.hg4idea.util.HgUtil.getNewBranchNameFromUser;

public class HgCreateNewBranchFromLogAction extends HgLogSingleCommitAction {
  @Override
  protected void actionPerformed(final @NotNull HgRepository repository, final @NotNull Hash commit) {
    final Project project = repository.getProject();
    FileDocumentManager.getInstance().saveAllDocuments();
    String shortHash = commit.toShortString();
    final String name = getNewBranchNameFromUser(repository, HgBundle.message("hg4idea.branch.create.from", shortHash));
    if (name != null) {
      new Task.Backgroundable(project, HgBundle.message("hg4idea.progress.updatingTo", shortHash)) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          if (HgUpdateCommand.updateRepoToInCurrentThread(project, repository.getRoot(), commit.asString(), false)) {
            new HgBranchPopupActions.HgNewBranchAction(project, Collections.singletonList(repository), repository)
              .createNewBranchInCurrentThread(name);
          }
        }
      }.queue();
    }
  }
}
