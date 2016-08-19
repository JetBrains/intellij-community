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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GitResetAction extends GitOneCommitPerRepoLogAction {

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
          Map<GitRepository, Hash> hashes = commits.keySet().stream().collect(
                                            Collectors.toMap(Function.identity(), repo -> commits.get(repo).getId()));
          new GitResetOperation(project, hashes, selectedMode, indicator).execute();
        }
      }.queue();
    }
  }

}
