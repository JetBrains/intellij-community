/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @Nullable
  @CalledInAny
  protected HgRepository getSelectedRepositoryFromEvent(@NotNull DataContext dataContext) {
    List<HgRepository> repositories = getSelectedRepositoriesFromEvent(dataContext);
    return repositories.isEmpty() ? null : repositories.get(0);
  }
}
