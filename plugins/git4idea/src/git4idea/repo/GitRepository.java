// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import git4idea.GitLocalBranch;
import git4idea.GitVcs;
import git4idea.branch.GitBranchesCollection;
import git4idea.ignore.GitRepositoryIgnoredFilesHolder;
import git4idea.status.GitStagingAreaHolder;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * <p>
 *   GitRepository is a representation of a Git repository stored under the specified directory.
 *   It stores the information about the repository, which is frequently requested by other plugin components.
 *   All get-methods (like {@link #getCurrentRevision()}) are just getters of the correspondent fields and thus are very fast.
 * </p>
 * <p>
 *   The GitRepository is updated "externally" by the {@link GitRepositoryUpdater}, when correspondent {@code .git/} service files
 *   change.
 * </p>
 * <p>
 *   To force asynchronous update, it is enough to call {@link VirtualFile#refresh(boolean, boolean) refresh} on the root directory.
 * </p>
 * <p>
 *   To make a synchronous update of the repository call {@link #update()}.
 *   Updating requires reading from disk, so it may take some time, however, updating the whole community repository took ~10 ms at the time
 *   of measurement, so must be fast enough. Better not to be called in AWT though.
 * </p>
 * <p>
 *   Other components may subscribe to GitRepository changes via the {@link #GIT_REPO_CHANGE} {@link Topic}
 * </p>
 *
 * <p>
 *   Getters and setters (update...()-methods) are not synchronized intentionally - to avoid live- and deadlocks.
 *   GitRepository is updated asynchronously,
 *   so even if the getters would have been synchronized, it wouldn't guarantee that they return actual values (as they are in .git).
 *   <br/>
 *   If one needs a really 100 % up-to-date value, one should call {@link #update()} and then get...().
 *   update() is a synchronous read from .git, so it is guaranteed to query the real value.
 * </p>
 *
 * @author Kirill Likhodedov
 */
@ApiStatus.NonExtendable
public interface GitRepository extends Repository {

  Topic<GitRepositoryChangeListener> GIT_REPO_CHANGE = Topic.create("GitRepository change",
                                                                    GitRepositoryChangeListener.class);
  Topic<GitRepositoryStateChangeListener> GIT_REPO_STATE_CHANGE = Topic.create("GitRepository state change",
                                                                               GitRepositoryStateChangeListener.class);

  /**
   * @deprecated Use #getRepositoryFiles(), since there will be two administrative directories if user uses git worktrees.
   */
  @Deprecated(forRemoval = true)
  @NotNull
  VirtualFile getGitDir();

  @NotNull
  GitRepositoryFiles getRepositoryFiles();

  @NotNull
  GitStagingAreaHolder getStagingAreaHolder();

  @NotNull
  GitUntrackedFilesHolder getUntrackedFilesHolder();


  @NotNull
  GitRepoInfo getInfo();

  /**
   * Returns the current branch of this Git repository.
   * If the repository is being rebased, then the current branch is the branch being rebased (which was current before the rebase
   * operation has started).
   * Returns null, if the repository is not on a branch and not in the REBASING state.
   */
  @Nullable
  GitLocalBranch getCurrentBranch();

  @NotNull
  GitBranchesCollection getBranches();

  /**
   * Returns remotes defined in this Git repository.
   */
  @NotNull
  Collection<GitRemote> getRemotes();

  @NotNull
  Collection<GitBranchTrackInfo> getBranchTrackInfos();

  @Nullable
  GitBranchTrackInfo getBranchTrackInfo(@NotNull String localBranchName);

  boolean isRebaseInProgress();

  boolean isOnBranch();

  @NotNull
  @Override
  GitVcs getVcs();

  /**
   * Returns direct submodules of this repository.
   */
  @NotNull
  Collection<GitSubmoduleInfo> getSubmodules();

  @NotNull
  GitRepositoryIgnoredFilesHolder getIgnoredFilesHolder();

  @NotNull
  CoroutineScope getCoroutineScope();

  @NotNull
  GitTagHolder getTagHolder();
}
