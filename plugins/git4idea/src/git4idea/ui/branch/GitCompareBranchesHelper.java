// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.dvcs.ui.CompareBranchesHelper;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;

public class GitCompareBranchesHelper implements CompareBranchesHelper {
  private final Project myProject;

  public GitCompareBranchesHelper(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull RepositoryManager getRepositoryManager() {
    return GitUtil.getRepositoryManager(myProject);
  }

  @Override
  public @NotNull DvcsCompareSettings getDvcsCompareSettings() {
    return GitVcsSettings.getInstance(myProject);
  }

  @Override
  public @NotNull String formatLogCommand(@NotNull String firstBranch, @NotNull String secondBranch) {
    return String.format("git log %s..%s", firstBranch, secondBranch); // NON-NLS
  }
}
