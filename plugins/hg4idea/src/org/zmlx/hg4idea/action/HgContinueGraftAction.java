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
import org.zmlx.hg4idea.command.HgGraftCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.GRAFT_CONTINUE_ERROR;

public class HgContinueGraftAction extends HgProcessStateAction {

  public HgContinueGraftAction() {
    super(Repository.State.GRAFTING);
  }

  @Override
  protected void execute(final @NotNull Project project,
                         @NotNull Collection<HgRepository> repositories,
                         final @Nullable HgRepository selectedRepo,
                         @NotNull DataContext dataContext) {

    new Task.Backgroundable(project, HgBundle.message("action.hg4idea.Graft.Continue.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (selectedRepo != null) {
          HgGraftCommand graftCommand = new HgGraftCommand(project, selectedRepo);
          HgCommandResult result = graftCommand.continueGrafting();
          if (HgErrorUtil.isAbort(result)) {
            new HgCommandResultNotifier(project)
              .notifyError(GRAFT_CONTINUE_ERROR,
                           result,
                           HgBundle.message("hg4idea.hg.error"),
                           HgBundle.message("action.hg4idea.Graft.continue.error"));
          }
          HgUtil.markDirectoryDirty(project, selectedRepo.getRoot());
        }
      }
    }.queue();
  }
}
