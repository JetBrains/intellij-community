// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class GitRebaseContinue extends GitAbstractRebaseAction {
  @NotNull
  @Nls
  @Override
  protected String getProgressTitle() {
    return GitBundle.message("rebase.progress.indicator.continue.title");
  }

  @Override
  protected void performActionForProject(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    GitRebaseUtils.continueRebase(project);
  }

  @Override
  protected void performActionForRepository(@NotNull Project project,
                                            @NotNull GitRepository repository,
                                            @NotNull ProgressIndicator indicator) {
    GitRebaseUtils.continueRebase(project, repository, indicator);
  }
}