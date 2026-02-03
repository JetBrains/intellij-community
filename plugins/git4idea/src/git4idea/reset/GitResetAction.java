// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.reset;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GitResetAction extends GitOneCommitPerRepoLogAction {

  @Override
  protected void actionPerformed(final @NotNull Project project, final @NotNull Map<GitRepository, VcsFullCommitDetails> commits) {
    GitVcsSettings settings = GitVcsSettings.getInstance(project);
    GitResetMode defaultMode = ObjectUtils.notNull(settings.getResetMode(), GitResetMode.getDefault());
    GitNewResetDialog dialog = new GitNewResetDialog(project, commits, defaultMode);
    if (dialog.showAndGet()) {
      final GitResetMode selectedMode = dialog.getResetMode();
      settings.setResetMode(selectedMode);
      new Task.Backgroundable(project, GitBundle.message("git.reset.process"), true) {
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
