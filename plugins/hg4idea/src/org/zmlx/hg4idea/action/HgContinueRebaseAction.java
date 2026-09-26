// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.command.HgRebaseCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.REBASE_CONTINUE_ERROR;

public class HgContinueRebaseAction extends HgProcessStateAction {

  public HgContinueRebaseAction() {
    super(Repository.State.REBASING);
  }

  @Override
  protected void execute(final @NotNull Project project,
                         @NotNull Collection<HgRepository> repositories,
                         final @Nullable HgRepository selectedRepo,
                         @NotNull DataContext dataContext) {

    new Task.Backgroundable(project, HgBundle.message("action.hg4idea.Rebase.Continue.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (selectedRepo != null) {
          HgRebaseCommand rebaseCommand = new HgRebaseCommand(project, selectedRepo);
          HgCommandResult result = rebaseCommand.continueRebase();
          if (HgErrorUtil.isAbort(result)) {
            new HgCommandResultNotifier(project).notifyError(REBASE_CONTINUE_ERROR,
                                                             result,
                                                             HgBundle.message("hg4idea.hg.error"),
                                                             HgBundle.message("action.hg4idea.Rebase.Continue.error"));
          }
          HgUtil.markDirectoryDirty(project, selectedRepo.getRoot());
        }
      }
    }.queue();
  }
}
