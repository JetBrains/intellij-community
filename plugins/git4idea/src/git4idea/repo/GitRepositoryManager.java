/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.project.Project;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.rebase.GitRebaseSpec;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GitRepositoryManager extends AbstractRepositoryManager<GitRepository> {

  @NotNull private final GitPlatformFacade myPlatformFacade;
  @NotNull private final Project myProject;

  @Nullable private volatile GitRebaseSpec myOngoingRebaseSpec;

  public GitRepositoryManager(@NotNull Project project, @NotNull GitPlatformFacade platformFacade,
                              @NotNull VcsRepositoryManager vcsRepositoryManager) {
    super(vcsRepositoryManager, platformFacade.getVcs(project), GitUtil.DOT_GIT);
    myProject = project;
    myPlatformFacade = platformFacade;
  }

  @Override
  public boolean isSyncEnabled() {
    return myPlatformFacade.getSettings(myProject).getSyncSetting() == DvcsSyncSettings.Value.SYNC &&
           !new GitMultiRootBranchConfig(getRepositories()).diverged();
  }

  @NotNull
  @Override
  public List<GitRepository> getRepositories() {
    return getRepositories(GitRepository.class);
  }

  @Nullable
  public GitRebaseSpec getOngoingRebaseSpec() {
    GitRebaseSpec rebaseSpec = myOngoingRebaseSpec;
    return rebaseSpec != null && rebaseSpec.isValid() ? rebaseSpec : null;
  }

  public boolean hasOngoingRebase() {
    return getOngoingRebaseSpec() != null;
  }

  public void setOngoingRebaseSpec(@Nullable GitRebaseSpec ongoingRebaseSpec) {
    myOngoingRebaseSpec = ongoingRebaseSpec != null && ongoingRebaseSpec.isValid() ? ongoingRebaseSpec : null;
  }
}
