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
package git4idea.branch;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchUiUtil;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchUtil {

  private static final Logger LOG = Logger.getInstance(GitBranchUtil.class);

  private GitBranchUtil() {}

  /**
   * Returns the tracking information about the given branch in the given repository,
   * or null if there is no such information (i.e. if the branch doesn't have a tracking branch).
   */
  @Nullable
  public static GitBranchTrackInfo getTrackInfoForBranch(@NotNull GitRepository repository, @NotNull GitBranch branch) {
    for (GitBranchTrackInfo trackInfo : repository.getConfig().getBranchTrackInfos()) {
      if (trackInfo.getBranch().equals(branch.getName())) {
        return trackInfo;
      }
    }
    return null;
  }

  /**
   * Looks through the remote branches in the given repository and tries to find the one from the given remote,
   * which the given name.
   * @return remote branch or null if such branch couldn't be found.
   */
  @Nullable
  public static GitBranch findRemoteBranchByName(@NotNull GitRepository repository, @Nullable GitRemote remote, @Nullable String name) {
    if (name == null || remote == null) {
      return null;
    }
    final String BRANCH_PREFIX = "refs/heads/";
    if (name.startsWith(BRANCH_PREFIX)) {
      name = name.substring(BRANCH_PREFIX.length());
    }

    for (GitBranch branch : repository.getBranches().getRemoteBranches()) {
      if (branch.getName().equals(remote.getName() + "/" + name)) {
        return branch;
      }
    }
    return null;
  }

  @NotNull
  static String getCurrentBranchOrRev(@NotNull Collection<GitRepository> repositories) {
    if (repositories.size() > 1) {
      GitMultiRootBranchConfig multiRootBranchConfig = new GitMultiRootBranchConfig(repositories);
      String currentBranch = multiRootBranchConfig.getCurrentBranch();
      LOG.assertTrue(currentBranch != null, "Repositories have unexpectedly diverged. " + multiRootBranchConfig);
      return currentBranch;
    }
    else {
      assert !repositories.isEmpty() : "No repositories passed to GitBranchOperationsProcessor.";
      GitRepository repository = repositories.iterator().next();
      return GitBranchUiUtil.getBranchNameOrRev(repository);
    }
  }

  @NotNull
  public static Collection<String> convertBranchesToNames(@NotNull Collection<GitBranch> branches) {
    return Collections2.transform(branches, new Function<GitBranch, String>() {
      @Override
      public String apply(@Nullable GitBranch input) {
        assert input != null;
        return input.getName();
      }
    });
  }

  /**
   * Returns the current branch in the given repository, or null if either repository is not on the branch, or in case of error.
   * @deprecated Use {@link git4idea.repo.GitRepository#getCurrentBranch()}
   */
  @Deprecated
  @Nullable
  public static GitBranch getCurrentBranch(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository != null) {
      return repository.getCurrentBranch();
    }
    else {
      LOG.error("Repository is null for root " + root);
    }
    return null;
  }

}
