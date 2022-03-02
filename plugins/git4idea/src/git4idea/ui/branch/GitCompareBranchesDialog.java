// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.intellij.dvcs.ui.CompareBranchesDialog;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true)
public class GitCompareBranchesDialog extends CompareBranchesDialog {
  public GitCompareBranchesDialog(@NotNull Project project,
                                  @NotNull String branchName,
                                  @NotNull String currentBranchName,
                                  @NotNull GitCommitCompareInfo compareInfo,
                                  @NotNull GitRepository initialRepo) {
    this(project, branchName, currentBranchName, compareInfo, initialRepo, false);
  }

  public GitCompareBranchesDialog(@NotNull Project project,
                                  @NotNull String branchName,
                                  @NotNull String currentBranchName,
                                  @NotNull GitCommitCompareInfo compareInfo,
                                  @NotNull GitRepository initialRepo,
                                  boolean dialog) {
    super(new GitCompareBranchesHelper(project), branchName, currentBranchName, compareInfo, initialRepo, dialog);
  }
}
