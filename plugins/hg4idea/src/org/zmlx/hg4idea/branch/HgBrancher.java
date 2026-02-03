// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.branch;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;

public class HgBrancher {
  private final Project myProject;

  HgBrancher(@NotNull Project project) {
    myProject = project;
  }

  public void compare(final @NotNull String branchName, final @NotNull List<HgRepository> repositories,
                      final @NotNull HgRepository selectedRepository) {
    new Task.Backgroundable(myProject, HgBundle.message("hg4idea.branch.comparing", branchName)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).compare(branchName, repositories, selectedRepository);
      }
    }.queue();
  }

  private HgBranchWorker newWorker(@SuppressWarnings("unused") ProgressIndicator indicator) {
    return new HgBranchWorker(myProject, indicator);
  }
}
