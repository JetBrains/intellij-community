// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.branch;

import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;
import java.util.function.Supplier;

public abstract class HgBranchAbstractAction extends DumbAwareAction {
  protected final @NotNull Project myProject;
  protected final @NotNull List<HgRepository> myRepositories;
  protected final @NotNull String myBranchName;

  public HgBranchAbstractAction(@NotNull Project project,
                                @NotNull Supplier<String> dynamicText,
                                @NotNull List<HgRepository> repositories,
                                @NotNull String branchName) {
    super(dynamicText);
    myProject = project;
    myRepositories = repositories;
    myBranchName = branchName;
  }
}