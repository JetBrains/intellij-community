// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public RepositoryManager getRepositoryManager() {
    return GitUtil.getRepositoryManager(myProject);
  }

  @Override
  @NotNull
  public DvcsCompareSettings getDvcsCompareSettings() {
    return GitVcsSettings.getInstance(myProject);
  }

  @Override
  @NotNull
  public String formatLogCommand(@NotNull String firstBranch, @NotNull String secondBranch) {
    return String.format("git log %s..%s", firstBranch, secondBranch);
  }
}
