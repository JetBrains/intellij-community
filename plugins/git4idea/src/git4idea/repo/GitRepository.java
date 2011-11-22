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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import git4idea.GitBranch;
import git4idea.branch.GitBranchesCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * <p>
 *   GitRepository is a representation of a Git repository stored under the specified directory.
 *   It stores the information about the repository, which is frequently requested by other plugin components.
 *   All get-methods (like {@link #getCurrentRevision()}) are just getters of the correspondent fields and thus are very fast.
 * </p>
 * <p>
 *   The GitRepository is updated "externally" by the {@link git4idea.repo.GitRepositoryUpdater}, when correspondent {@code .git/} service files
 *   change.
 * </p>
 * <p>
 *   To force asynchronous update, it is enough to call {@link VirtualFile#refresh(boolean, boolean) refresh} on the root directory.
 * </p>
 * <p>
 *   To make a synchronous update of the repository call {@link #update(TrackedTopic...)} and specify
 *   which topics should be updated. Updating requires reading from disk, so updating {@link GitRepository.TrackedTopic.ALL} may take some time.
 * </p>
 * <p>
 *   Other components may subscribe to GitRepository changes via the {@link #GIT_REPO_CHANGE} {@link Topic}
 *   or preferably via {@link GitRepositoryManager#addListenerToAllRepositories(GitRepositoryChangeListener)}
 * </p>
 *
 * <p>
 *   Getters and setters (update...()-methods) are not synchronized intentionally - to avoid live- and deadlocks.
 *   GitRepository is updated asynchronously,
 *   so even if the getters would have been synchronized, it wouldn't guarantee that they return actual values (as they are in .git).
 *   <br/>
 *   If one needs an up-to-date value, one should call update(TrackedTopic...) and then get...().
 *   update() is a synchronous read from .git, so it is guaranteed to query the real value.
 * </p>
 *
 * @author Kirill Likhodedov
 */
public final class GitRepository implements Disposable {

  public static final Topic<GitRepositoryChangeListener> GIT_REPO_CHANGE = Topic.create("GitRepository change", GitRepositoryChangeListener.class);

  private static final Object STUB_OBJECT = new Object();

  private final Project myProject;
  private final VirtualFile myRootDir;
  private final GitRepositoryReader myReader;
  private final VirtualFile myGitDir;
  private final MessageBus myMessageBus;
  private final GitUntrackedFilesHolder myUntrackedFilesHolder;
  private final QueueProcessor<Object> myNotifier;

  private volatile State myState;
  private volatile String myCurrentRevision;
  private volatile GitBranch myCurrentBranch;
  private volatile GitBranchesCollection myBranches = GitBranchesCollection.EMPTY;
  private volatile GitConfig myConfig;

  /**
   * Current state of the repository.
   */
  public enum State {
    /**
     * HEAD is on branch, no merge process is in progress (and no rebase as well).
     */
    NORMAL,
    /**
     * During merge (for instance, merge failed with conflicts that weren't immediately resolved).
     */
    MERGING {
      @Override public String toString() {
        return "Merging";
      }
    },
    /**
     * During rebase.
     */
    REBASING {
      @Override public String toString() {
        return "Rebasing";
      }
    },
    /**
     * Detached HEAD state, but not during rebase (for example, manual checkout of a commit hash).
     */
    DETACHED
  }

  /**
   * GitRepository tracks the updates of some information about Git repository, caches this information and provides methods to access to
   * it. The pieces of this information are called Topics. They can be used to update the repository.
   */
  public enum TrackedTopic {
    STATE {
      @Override void update(GitRepository repository) {
        repository.updateState();
      }
    },
    CURRENT_REVISION {
      @Override void update(GitRepository repository) {
        repository.updateCurrentRevision();
      }
    },
    CURRENT_BRANCH {
      @Override void update(GitRepository repository) {
        repository.updateCurrentBranch();
      }
    },
    BRANCHES {
      @Override void update(GitRepository repository) {
        repository.updateBranchList();        
      }
    },
    CONFIG {
      @Override void update(GitRepository repository) {
        repository.updateConfig();
      }
    },
    ALL_CURRENT {
      @Override void update(GitRepository repository) {
        STATE.update(repository);
        CURRENT_REVISION.update(repository);
        CURRENT_BRANCH.update(repository);
      }
    },
    ALL {
      @Override void update(GitRepository repository) {
        ALL_CURRENT.update(repository);
        BRANCHES.update(repository);
        CONFIG.update(repository);
      }
    };

    abstract void update(GitRepository repository);
  }

  /**
   * Get the GitRepository instance from the {@link GitRepositoryManager}.
   * If you need to have an instance of GitRepository for a repository outside the project, use
   * {@link #getLightInstance(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.project.Project, com.intellij.openapi.Disposable)}.
   */
  private GitRepository(@NotNull VirtualFile rootDir, @NotNull Project project, @NotNull Disposable parentDisposable) {
    myRootDir = rootDir;
    myProject = project;
    Disposer.register(parentDisposable, this);
    
    myReader = new GitRepositoryReader(this);
    
    myGitDir = myRootDir.findChild(".git");
    assert myGitDir != null : ".git directory wasn't found under " + rootDir.getPresentableUrl();
    
    myUntrackedFilesHolder = new GitUntrackedFilesHolder(rootDir, project);
    Disposer.register(this, myUntrackedFilesHolder);

    myMessageBus = project.getMessageBus();
    myNotifier = new QueueProcessor<Object>(new NotificationConsumer(myProject, myMessageBus), myProject.getDisposed());
    update(TrackedTopic.ALL);
  }

  /**
   * Returns the temporary light instance of GitRepository.
   * It lacks functionality of auto-updating GitRepository on Git internal files change, and also stored a stub instance of 
   * {@link GitUntrackedFilesHolder}.
   */
  @NotNull
  public static GitRepository getLightInstance(@NotNull VirtualFile root, @NotNull Project project, @NotNull Disposable parentDisposable) {
    return new GitRepository(root, project, parentDisposable);
  }

  /**
   * Returns the full-functional instance of GitRepository - with UntrackedFilesHolder and GitRepositoryUpdater.
   * This is used for repositories registered in project, and should be optained via {@link GitRepositoryManager}.
   */
  static GitRepository getFullInstance(@NotNull VirtualFile root, @NotNull Project project, @NotNull Disposable parentDisposable) {
    GitRepository repository = new GitRepository(root, project, parentDisposable);
    repository.myUntrackedFilesHolder.setupVfsListener(project);
    repository.setupUpdater();
    return repository;
  }

  private void setupUpdater() {
    GitRepositoryUpdater updater = new GitRepositoryUpdater(this);
    Disposer.register(this, updater);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRootDir;
  }

  @NotNull
  public String getPresentableUrl() {
    return getRoot().getPresentableUrl();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public GitUntrackedFilesHolder getUntrackedFilesHolder() {
    return myUntrackedFilesHolder;
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

  /**
   * @return local and remote branches in this repository.
   */
  @NotNull
  public GitBranchesCollection getBranches() {
    return new GitBranchesCollection(myBranches);
  }

  @NotNull
  public GitConfig getConfig() {
    return myConfig;
  }

  /**
   * Returns remotes defined in this Git repository.
   * It is different from {@link git4idea.repo.GitConfig#getRemotes()} because remotes may be defined not only in {@code .git/config},
   * but in {@code .git/remotes/} or even {@code .git/branches} as well.
   * On the other hand, it is a very old way to define remotes and we are not going to implement this until needed.
   * See <a href="http://thread.gmane.org/gmane.comp.version-control.git/182960">discussion in the Git mailing list</a> that confirms
   * that remotes a defined in {@code .git/config} only nowadays.
   * @return GitRemotes defined for this repository.
   */
  @NotNull
  public Collection<GitRemote> getRemotes() {
    return myConfig.getRemotes();
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

  /**
   * @return true if current repository is "fresh", i.e. if no commits have been made yet.
   */
  public boolean isFresh() {
    return getCurrentRevision() == null;
  }

  public void addListener(GitRepositoryChangeListener listener) {
    MessageBusConnection connection = myMessageBus.connect();
    Disposer.register(this, connection);
    connection.subscribe(GIT_REPO_CHANGE, listener);
  }

  /**
   * Synchronously updates the GitRepository by reading information from the specified topics.
   */
  public void update(TrackedTopic... topics) {
    for (TrackedTopic topic : topics) {
      topic.update(this);
    }
    notifyListeners();
  }
  
  private void updateConfig() {
    File configFile = new File(VfsUtil.virtualToIoFile(myGitDir), "config");
    myConfig = GitConfig.read(configFile);
  }

  /**
   * Reads current state and notifies listeners about the change.
   */
  private void updateState() {
    myState = myReader.readState();
  }

  /**
   * Reads current revision and notifies listeners about the change.
   */
  private void updateCurrentRevision() {
    myCurrentRevision = myReader.readCurrentRevision();
  }

  /**
   * Reads current branch and notifies listeners about the change.
   */
  private void updateCurrentBranch() {
    myCurrentBranch = myReader.readCurrentBranch();
  }
  
  private void updateBranchList() {
    myBranches = myReader.readBranches();
  }

  private void notifyListeners() {
    myNotifier.add(STUB_OBJECT);     // we don't have parameters for listeners
  }

  private static class NotificationConsumer implements Consumer<Object> {

    private final Project myProject;
    private final MessageBus myMessageBus;

    NotificationConsumer(Project project, MessageBus messageBus) {
      myProject = project;
      myMessageBus = messageBus;
    }

    @Override
    public void consume(Object o) {
      if (!Disposer.isDisposed(myProject)) {
        myMessageBus.syncPublisher(GIT_REPO_CHANGE).repositoryChanged();
      }
    }
  }

  public String toLogString() {
    return String.format("GitRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s, myRootDir=%s}",
                         myCurrentBranch, myCurrentRevision, myState, myRootDir);
  }
  
  @Override
  public String toString() {
    return getPresentableUrl();
  }
}
