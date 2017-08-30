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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgPullCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgPullDialog;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;

public class HgPullAction extends HgAbstractGlobalSingleRepoAction {

  @Override
  protected void execute(@NotNull final Project project, @NotNull Collection<HgRepository> repos, @Nullable HgRepository selectedRepo) {
    final HgPullDialog dialog = new HgPullDialog(project, repos, selectedRepo);
    if (dialog.showAndGet()) {
      final String source = dialog.getSource();
      final HgRepository hgRepository = dialog.getRepository();
      new Task.Backgroundable(project, "Pulling changes from " + source, false) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          executePull(project, hgRepository, source);
          HgUtil.markDirectoryDirty(project, hgRepository.getRoot());
        }
      }.queue();
    }
  }

  private static void executePull(final Project project, final HgRepository hgRepository, final String source) {
    final HgPullCommand command = new HgPullCommand(project, hgRepository.getRoot());
    command.setSource(source);
    command.executeInCurrentThread();
    hgRepository.update();
  }
}
