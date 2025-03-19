// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;
import java.util.List;

public abstract class HgAbstractGlobalSingleRepoAction extends HgAbstractGlobalAction {

  @Override
  protected void execute(@NotNull Project project,
                         @NotNull Collection<HgRepository> repositories,
                         @NotNull List<HgRepository> selectedRepositories,
                         @NotNull DataContext context) {
    execute(project, repositories, selectedRepositories.isEmpty() ? null : selectedRepositories.get(0), context);
  }

  protected abstract void execute(@NotNull Project project,
                                  @NotNull Collection<HgRepository> repositories,
                                  @Nullable HgRepository selectedRepo,
                                  @NotNull DataContext dataContext);

  @CalledInAny
  protected @Nullable HgRepository getSelectedRepositoryFromEvent(@NotNull DataContext dataContext) {
    List<HgRepository> repositories = getSelectedRepositoriesFromEvent(dataContext);
    return repositories.isEmpty() ? null : repositories.get(0);
  }
}
