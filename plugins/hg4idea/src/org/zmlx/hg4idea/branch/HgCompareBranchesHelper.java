// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.dvcs.ui.CompareBranchesHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.util.HgUtil;

public class HgCompareBranchesHelper implements CompareBranchesHelper {
  private final Project myProject;

  public HgCompareBranchesHelper(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull RepositoryManager getRepositoryManager() {
    return HgUtil.getRepositoryManager(myProject);
  }

  @Override
  public @NotNull DvcsCompareSettings getDvcsCompareSettings() {
    return HgProjectSettings.getInstance(myProject);
  }

  @Override
  public @NlsSafe @NotNull String formatLogCommand(@NotNull String firstBranch, @NotNull String secondBranch) {
    return String.format("hg log -r \"reverse(%s%%%s)\"", secondBranch, firstBranch);
  }
}
