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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgRunConflictResolverDialog;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;

public class HgRunConflictResolverAction extends HgAbstractGlobalSingleRepoAction {

  @Override
  public void execute(@NotNull final Project project, @NotNull Collection<HgRepository> repositories, @Nullable HgRepository selectedRepo) {
    final HgRepository repository = repositories.size() > 1 ? letUserSelectRepository(project, repositories, selectedRepo) :
                                    ContainerUtil.getFirstItem(repositories);
    if (repository != null) {
      new Task.Backgroundable(project, HgVcsMessages.message("action.hg4idea.run.conflict.resolver.description")) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          new HgConflictResolver(project).resolve(repository.getRoot());
          HgUtil.markDirectoryDirty(project, repository.getRoot());
        }
      }.queue();
    }
  }

  @Nullable
  private static HgRepository letUserSelectRepository(@NotNull Project project, @NotNull Collection<HgRepository> repositories,
                                                      @Nullable HgRepository selectedRepo) {
    HgRunConflictResolverDialog dialog = new HgRunConflictResolverDialog(project, repositories, selectedRepo);
    return dialog.showAndGet() ? dialog.getRepository() : null;
  }
}
