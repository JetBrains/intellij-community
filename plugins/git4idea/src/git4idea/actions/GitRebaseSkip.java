/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

public class GitRebaseSkip extends GitAbstractRebaseAction {
  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Skip Commit during Rebase...";
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