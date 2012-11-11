/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.GitLogger;
import git4idea.GitPlatformFacade;
import git4idea.branch.GitBranchPair;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.settings.GitSyncRepoSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Utilities specific to push.
 *
 * @author Kirill Likhodedov
 */
class GitPushUtil {

  private static final Logger LOG = GitLogger.PUSH_LOG;

  @NotNull
  static GitPushSpecs getRepositoriesAndSpecsToPush(@NotNull GitPlatformFacade facade, @NotNull Project project) {
    GitSyncRepoSetting syncSetting = facade.getSettings(project).getSyncSetting();

    if (syncSetting.equals(GitSyncRepoSetting.SYNC)) {
      return getSpecsForAllRepositories(facade, project, null);
    }
    else {
      if (facade.getRepositoryManager(project).moreThanOneRoot() && syncSetting.equals(GitSyncRepoSetting.NOT_DECIDED)) {
        LOG.warn("The sync setting should have already been initialized");
      }
      GitRepository repository = GitBranchUtil.getCurrentRepository(project);
      if (repository == null) {
        LOG.warn("Couldn't retrieve current repository");
      }
      return getSpecsForAllRepositories(facade, project, repository);
    }
  }

  @NotNull
  private static GitPushSpecs getSpecsForAllRepositories(@NotNull GitPlatformFacade facade, @NotNull Project project,
                                                         @Nullable GitRepository selectedRepository) {
    List<GitRepository> repositories = facade.getRepositoryManager(project).getRepositories();
    GitPushSpecs specs = new GitPushSpecs();
    for (GitRepository repository : repositories) {
      boolean selected = selectedRepository == null || repository.equals(selectedRepository);
      specs.put(repository, GitBranchPair.findCurrentAnTracked(repository), selected);
    }
    return specs;
  }
}
