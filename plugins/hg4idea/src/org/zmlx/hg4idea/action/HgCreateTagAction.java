// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgTagCreateCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgTagDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.Collection;

public class HgCreateTagAction extends HgAbstractGlobalSingleRepoAction {

  public void execute(@NotNull final Project project,
                      @NotNull Collection<HgRepository> repositories,
                      @Nullable HgRepository selectedRepo,
                      @Nullable final String reference) {
    final HgTagDialog dialog = new HgTagDialog(project, repositories, selectedRepo);
    if (dialog.showAndGet()) {
      try {
        new HgTagCreateCommand(project, dialog.getRepository(), dialog.getTagName(), reference).execute(new HgCommandResultHandler() {
          @Override
          public void process(@Nullable HgCommandResult result) {
            if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
              new HgCommandResultNotifier(project)
                .notifyError(result, "Creation failed", "Tag creation [" + dialog.getTagName() + "] failed");
            }
          }
        });
      }
      catch (HgCommandException e) {
        HgErrorUtil.handleException(project, e);
      }
    }
  }

  protected void execute(@NotNull final Project project,
                         @NotNull Collection<HgRepository> repositories,
                         @Nullable HgRepository selectedRepo) {
    execute(project, repositories, selectedRepo, null);
  }
}
