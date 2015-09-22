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
package git4idea.reset;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.VcsLogOneCommitPerRepoAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class GitResetAction extends VcsLogOneCommitPerRepoAction<GitRepository> {

  @NotNull
  @Override
  protected AbstractRepositoryManager<GitRepository> getRepositoryManager(@NotNull Project project) {
    return ServiceManager.getService(project, GitRepositoryManager.class);
  }

  @Nullable
  @Override
  protected GitRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return getRepositoryManager(project).getRepositoryForRoot(root);
  }

  @Override
  protected void actionPerformed(@NotNull final Project project, @NotNull final Map<GitRepository, VcsFullCommitDetails> commits) {
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    GitResetMode defaultMode = ObjectUtils.notNull(settings.getResetMode(), GitResetMode.getDefault());
    GitNewResetDialog dialog = new GitNewResetDialog(project, commits, defaultMode);
    if (dialog.showAndGet()) {
      final GitResetMode selectedMode = dialog.getResetMode();
      settings.setResetMode(selectedMode);
      new Task.Backgroundable(project, "Git reset", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          new GitResetOperation(project, commits, selectedMode, indicator).execute();
        }
      }.queue();
    }
  }

}
