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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.*;
import git4idea.config.GitConfigUtil;
import git4idea.repo.*;
import git4idea.ui.branch.GitBranchUiUtil;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;

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
    for (GitBranchTrackInfo trackInfo : repository.getBranchTrackInfos()) {
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
  public static Collection<String> convertBranchesToNames(@NotNull Collection<? extends GitBranch> branches) {
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
   * @deprecated Use {@link GitRepository#getCurrentBranch()}
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

  /**
   * Get tracked remote for the branch
   */
  @Nullable
  public static String getTrackedRemoteName(Project project, VirtualFile root, String branchName) throws VcsException {
    return GitConfigUtil.getValue(project, root, trackedRemoteKey(branchName));
  }

  /**
   * Get tracked branch of the given branch
   */
  @Nullable
  public static String getTrackedBranchName(Project project, VirtualFile root, String branchName) throws VcsException {
    return GitConfigUtil.getValue(project, root, trackedBranchKey(branchName));
  }

  @NotNull
  private static String trackedBranchKey(String branchName) {
    return "branch." + branchName + ".merge";
  }

  @NotNull
  private static String trackedRemoteKey(String branchName) {
    return "branch." + branchName + ".remote";
  }

  /**
   * Get the tracking branch for the given branch, or null if the given branch doesn't track anything.
   * @deprecated Use {@link GitConfig#getBranchTrackInfos()}
   */
  @Deprecated
  @Nullable
  public static GitBranch tracked(@NotNull Project project, @NotNull VirtualFile root, @NotNull String branchName) throws VcsException {
    final HashMap<String, String> result = new HashMap<String, String>();
    GitConfigUtil.getValues(project, root, null, result);
    String remoteName = result.get(trackedRemoteKey(branchName));
    if (remoteName == null) {
      return null;
    }
    String branch = result.get(trackedBranchKey(branchName));
    if (branch == null) {
      return null;
    }

    if (".".equals(remoteName)) {
      return new GitSvnRemoteBranch(branch, GitBranch.DUMMY_HASH);
    }

    GitRemote remote = findRemoteByNameOrLogError(project, root, remoteName);
    if (remote == null) return null;
    return new GitRemoteBranch(remote, branch, GitBranch.DUMMY_HASH);
  }

  @Nullable
  @Deprecated
  public static GitRemote findRemoteByNameOrLogError(@NotNull Project project, @NotNull VirtualFile root, @NotNull String remoteName) {
    GitRepository repository = GitUtil.getRepositoryForRootOrLogError(project, root);
    if (repository == null) {
      return null;
    }

    GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
    if (remote == null) {
      LOG.warn("Couldn't find remote with name " + remoteName);
      return null;
    }
    return remote;
  }

  /**
   *
   * @return {@link GitRemoteBranch} or {@link GitSvnRemoteBranch}, or null in case of an error. The error is logged in this method.
   * @deprecated Should be used only in the GitRepositoryReader, i. e. moved there once all other usages are removed.
   */
  @Deprecated
  @Nullable
  public static GitBranch parseRemoteBranch(@NotNull String fullBranchName, @NotNull Hash hash, @NotNull Collection<GitRemote> remotes) {
    String stdName = fullBranchName.substring(GitBranch.REFS_REMOTES_PREFIX.length());

    int slash = stdName.indexOf('/');
    if (slash == -1) { // .git/refs/remotes/my_branch => git-svn
      return new GitSvnRemoteBranch(fullBranchName, hash);
    }
    else {
      String remoteName = stdName.substring(0, slash);
      String branchName = stdName.substring(slash + 1);
      GitRemote remote = findRemoteByName(remoteName, remotes);
      if (remote == null) {
        return null;
      }
      return new GitRemoteBranch(remote, branchName, hash);
    }
  }

  @Nullable
  private static GitRemote findRemoteByName(@NotNull String remoteName, @NotNull Collection<GitRemote> remotes) {
    for (GitRemote remote : remotes) {
      if (remote.getName().equals(remoteName)) {
        return remote;
      }
    }
    // user may remove the remote section from .git/config, but leave remote refs untouched in .git/refs/remotes
    LOG.info(String.format("No remote found with the name [%s]. All remotes: %s", remoteName, remotes));
    return null;
  }

}
