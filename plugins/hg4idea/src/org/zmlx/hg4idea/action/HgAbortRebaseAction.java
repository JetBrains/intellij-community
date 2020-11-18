/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.dvcs.repo.Repository;
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

import static org.zmlx.hg4idea.HgNotificationIdsHolder.REBASE_ABORT_ERROR;

public class HgAbortRebaseAction extends HgProcessStateAction {

  public HgAbortRebaseAction() {
    super(Repository.State.REBASING);
  }

  @Override
  protected void execute(@NotNull final Project project,
                         @NotNull Collection<HgRepository> repositories,
                         @Nullable final HgRepository selectedRepo) {

    new Task.Backgroundable(project, HgBundle.message("action.hg4idea.Rebase.Abort.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (selectedRepo != null) {
          HgRebaseCommand rebaseCommand = new HgRebaseCommand(project, selectedRepo);
          HgCommandResult result = rebaseCommand.abortRebase();
          if (HgErrorUtil.isAbort(result)) {
            new HgCommandResultNotifier(project).notifyError(REBASE_ABORT_ERROR,
                                                             result,
                                                             HgBundle.message("hg4idea.hg.error"),
                                                             HgBundle.message("action.hg4idea.Rebase.Abort.error"));
          }
          HgUtil.markDirectoryDirty(project, selectedRepo.getRoot());
        }
      }
    }.queue();
  }
}
