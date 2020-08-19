// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public RepositoryManager getRepositoryManager() {
    return HgUtil.getRepositoryManager(myProject);
  }

  @Override
  @NotNull
  public DvcsCompareSettings getDvcsCompareSettings() {
    return HgProjectSettings.getInstance(myProject);
  }

  @NlsSafe
  @Override
  @NotNull
  public String formatLogCommand(@NotNull String firstBranch, @NotNull String secondBranch) {
    return String.format("hg log -r \"reverse(%s%%%s)\"", secondBranch, firstBranch);
  }
}
