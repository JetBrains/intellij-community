// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class GitRebaseSkip extends GitAbstractRebaseAction {
  @Override
  protected @NlsContexts.ProgressTitle @NotNull String getProgressTitle() {
    return GitBundle.message("action.Git.Rebase.Skip.progress.title");
  }

  @Override
  public @NotNull Icon getMainToolbarIcon() {
    return DvcsImplIcons.SkipCommit;
  }

  @Override
  protected void performActionForProject(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    GitRebaseUtils.skipRebase(project);
  }

  @Override
  protected void performActionForRepository(@NotNull Project project,
                                            @NotNull GitRepository repository,
                                            @NotNull ProgressIndicator indicator) {
    GitRebaseUtils.skipRebase(project, repository, indicator);
  }
}