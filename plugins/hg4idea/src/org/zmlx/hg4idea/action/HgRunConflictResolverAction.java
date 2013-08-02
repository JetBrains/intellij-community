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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.ui.HgRunConflictResolverDialog;

import java.util.Collection;

public class HgRunConflictResolverAction extends HgAbstractGlobalAction {

  @Override
  public void execute(@NotNull final Project project, @NotNull Collection<VirtualFile> repos, @Nullable VirtualFile selectedRepo) {
    final VirtualFile repository;
    if (repos.size() > 1) {
      repository = letUserSelectRepository(repos, project, selectedRepo);
    }
    else if (repos.size() == 1) {
      repository = repos.iterator().next();
    }
    else {
      repository = null;
    }
    if (repository != null) {
      new Task.Backgroundable(project, HgVcsMessages.message("action.hg4idea.run.conflict.resolver.description")) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          new HgConflictResolver(project).resolve(repository);
          markDirtyAndHandleErrors(project, repository);
        }
      }.queue();
    }
  }


  private static VirtualFile letUserSelectRepository(Collection<VirtualFile> repos, Project project, @Nullable VirtualFile selectedRepo) {
    HgRunConflictResolverDialog dialog = new HgRunConflictResolverDialog(project);
    dialog.setRoots(repos, selectedRepo);
    dialog.show();
    if (dialog.isOK()) {
      return dialog.getRepository();
    }
    else {
      return null;
    }
  }
}
