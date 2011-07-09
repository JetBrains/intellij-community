/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import git4idea.GitBranch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 *   GitRepository is a representation of the Git repository stored under the specified directory.
 *   Its stores the information about the repository, which is frequently requested by other plugin components,
 *   thus all get-methods (like {@link #getCurrentRevision()}) are just getters of the correspondent fields.
 * </p>
 * <p>
 *   The GitRepository is updated "externally" by the {@link git4idea.repo.GitRepositoryUpdater}, when correspondent .git service files
 *   change. To force the update procedure call {@link #refresh()}.
 * </p>
 * <p>
 *   Other components may subscribe to GitRepository changes via the {@link #GIT_REPO_CHANGE} {@link Topic}
 *   or preferably via {@link GitRepositoryManager#addListenerToAllRepositories(GitRepositoryChangeListener)}
 * </p>
 *
 * @author Kirill Likhodedov
 */
public class GitRepository implements Disposable {

  public static final Topic<GitRepositoryChangeListener> GIT_REPO_CHANGE = Topic.create("GitRepository change", GitRepositoryChangeListener.class);

  private final VirtualFile myRootDir;
  private final GitRepositoryReader myReader;
  private final VirtualFile myGitDir;
  private final MessageBus myMessageBus;
  private final MessageBusConnection myMessageBusConnection;
  private final GitRepositoryUpdater myUpdater;

  private State myState;
  private String myCurrentRevision;
  private GitBranch myCurrentBranch;

  /**
   * Current state of the repository.
   * NORMAL   - HEAD is on branch, no merge process is in progress.
   * MERGING  - during merge (for instance, merge failed with conflicts that weren't immediately resolved).
   * REBASING - during rebase.
   * DETACHED - detached HEAD state, but not during rebase.
   */
  public enum State {
    NORMAL,
    MERGING {
      @Override public String toString() {
        return "Merging";
      }
    },
    REBASING {
      @Override public String toString() {
        return "Rebasing";
      }
    },
    DETACHED
  }

  /**
   * Don't use this constructor - get the GitRepository instance from the {@link GitRepositoryManager}.
   */
  GitRepository(@NotNull VirtualFile rootDir) {
    myRootDir = rootDir;
    myReader = new GitRepositoryReader(this);
    myUpdater = new GitRepositoryUpdater(this);
    Disposer.register(this, myUpdater);

    myGitDir = myRootDir.findChild(".git");
    assert myGitDir != null : ".git directory wasn't found under " + rootDir.getPresentableUrl();

    myMessageBus = ApplicationManager.getApplication().getMessageBus();
    myMessageBusConnection = myMessageBus.connect();
    Disposer.register(this, myMessageBusConnection);

    fullReRead();
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRootDir;
  }

  @NotNull
  public State getState() {
    return myState;
  }

  /**
   * Returns the hash of the revision, which HEAD currently points to.
   * Returns null only in the case of a fresh repository, when no commit have been made.
   */
  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  /**
   * Returns the current branch of this Git repository.
   * If the repository is being rebased, then the current branch is the branch being rebased (which was current before the rebase
   * operation has started).
   * Returns null, if the repository is not on a branch and not in the REBASING state.
   */
  @Nullable
  public GitBranch getCurrentBranch() {
    return myCurrentBranch;
  }

  public boolean isMergeInProgress() {
    return getState() == State.MERGING;
  }

  public boolean isRebaseInProgress() {
    return getState() == State.REBASING;
  }

  public boolean isOnBranch() {
    return getState() != State.DETACHED && getState() != State.REBASING;
  }

  public void addListener(GitRepositoryChangeListener listener) {
    myMessageBusConnection.subscribe(GIT_REPO_CHANGE, listener);
  }

  /**
   * Refreshes the .git directory asynchronously.
   * Call this method after performing write operations on the Git repository: such as commit, fetch, reset, etc.
   */
  public void refresh() {
    myGitDir.refresh(true, true);
  }

  /**
   * Re-reads all the information from .git
   */
  private void fullReRead() {
    updateState();
    updateCurrentBranch();
    updateCurrentRevision();
  }

  /**
   * Reads current state and notifies listeners about the change.
   */
  void updateState() {
    myState = myReader.readState();
    notifyListeners();
  }

  /**
   * Reads current revision and notifies listeners about the change.
   */
  void updateCurrentRevision() {
    myCurrentRevision = myReader.readCurrentRevision();
    notifyListeners();
  }

  /**
   * Reads current branch and notifies listeners about the change.
   */
  void updateCurrentBranch() {
    myCurrentBranch = myReader.readCurrentBranch();
    notifyListeners();
  }

  private void notifyListeners() {
    myMessageBus.syncPublisher(GIT_REPO_CHANGE).repositoryChanged();
  }

}
