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
package git4idea.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.GitBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.branch.GitBranchesCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class GitRepositoryImpl implements GitRepository, Disposable {

  private static final Object STUB_OBJECT = new Object();

  private final Project myProject;
  @NotNull private final GitPlatformFacade myPlatformFacade;
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
   * Get the GitRepository instance from the {@link GitRepositoryManager}.
   * If you need to have an instance of GitRepository for a repository outside the project, use
   * {@link #getLightInstance(VirtualFile, Project, PlatformFacade, Disposable)}.
   */
  protected GitRepositoryImpl(@NotNull VirtualFile rootDir, @NotNull GitPlatformFacade facade, @NotNull Project project,
                            @NotNull Disposable parentDisposable, final boolean light) {
    myRootDir = rootDir;
    myPlatformFacade = facade;
    myProject = project;
    Disposer.register(parentDisposable, this);

    myGitDir = GitUtil.findGitDir(myRootDir);
    assert myGitDir != null : ".git directory wasn't found under " + rootDir.getPresentableUrl();

    myReader = new GitRepositoryReader(VfsUtilCore.virtualToIoFile(myGitDir));

    myMessageBus = project.getMessageBus();
    myNotifier = new QueueProcessor<Object>(new NotificationConsumer(myProject, myMessageBus), myProject.getDisposed());
    if (!light) {
      myUntrackedFilesHolder = new GitUntrackedFilesHolder(this);
      Disposer.register(this, myUntrackedFilesHolder);
    } else {
      myUntrackedFilesHolder = null;
    }
    update(TrackedTopic.ALL);
  }

  /**
   * Returns the temporary light instance of GitRepository.
   * It lacks functionality of auto-updating GitRepository on Git internal files change, and also stored a stub instance of
   * {@link GitUntrackedFilesHolder}.
   */
  @NotNull
  public static GitRepository getLightInstance(@NotNull VirtualFile root, @NotNull Project project, @NotNull GitPlatformFacade facade,
                                               @NotNull Disposable parentDisposable) {
    return new GitRepositoryImpl(root, facade, project, parentDisposable, true);
  }

  /**
   * Returns the full-functional instance of GitRepository - with UntrackedFilesHolder and GitRepositoryUpdater.
   * This is used for repositories registered in project, and should be optained via {@link GitRepositoryManager}.
   */
  public static GitRepository getFullInstance(@NotNull VirtualFile root, @NotNull Project project, @NotNull GitPlatformFacade facade,
                                              @NotNull Disposable parentDisposable) {
    GitRepositoryImpl repository = new GitRepositoryImpl(root, facade, project, parentDisposable, false);
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

  @Override
  @NotNull
  public VirtualFile getRoot() {
    return myRootDir;
  }

  @Override
  @NotNull
  public VirtualFile getGitDir() {
    return myGitDir;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    return getRoot().getPresentableUrl();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public GitUntrackedFilesHolder getUntrackedFilesHolder() {
    if (myUntrackedFilesHolder == null) {
      throw new IllegalStateException("Using untracked files holder with light git repository instance " + this);
    }
    return myUntrackedFilesHolder;
  }

  @Override
  @NotNull
  public State getState() {
    return myState;
  }

  @Override
  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  @Override
  @Nullable
  public GitBranch getCurrentBranch() {
    return myCurrentBranch;
  }

  /**
   * @return local and remote branches in this repository.
   */
  @Override
  @NotNull
  public GitBranchesCollection getBranches() {
    return new GitBranchesCollection(myBranches);
  }

  @Override
  @NotNull
  public GitConfig getConfig() {
    return myConfig;
  }

  @Override
  @NotNull
  public Collection<GitRemote> getRemotes() {
    return myConfig.getRemotes();
  }

  @Override
  public boolean isMergeInProgress() {
    return getState() == State.MERGING;
  }

  @Override
  public boolean isRebaseInProgress() {
    return getState() == State.REBASING;
  }

  @Override
  public boolean isOnBranch() {
    return getState() != State.DETACHED && getState() != State.REBASING;
  }

  @Override
  public boolean isFresh() {
    return getCurrentRevision() == null;
  }

  @Override
  public void addListener(@NotNull GitRepositoryChangeListener listener) {
    MessageBusConnection connection = myMessageBus.connect();
    Disposer.register(this, connection);
    connection.subscribe(GIT_REPO_CHANGE, listener);
  }

  @Override
  public void update(TrackedTopic... topics) {
    for (TrackedTopic topic : topics) {
      switch (topic) {
        case STATE:            updateState(); break;
        case CURRENT_REVISION: updateCurrentRevision(); break;
        case CURRENT_BRANCH:   updateCurrentBranch(); break;
        case BRANCHES:         updateBranchList(); break;
        case CONFIG:           updateConfig(); break;
        case ALL_CURRENT:
          updateState();
          updateCurrentRevision();
          updateCurrentBranch();
          break;
        case ALL:
          updateState();
          updateCurrentRevision();
          updateCurrentBranch();
          updateBranchList();
          updateConfig();
          break;
      }
    }
    notifyListeners();
  }

  private void updateConfig() {
    File configFile = new File(VfsUtilCore.virtualToIoFile(myGitDir), "config");
    myConfig = GitConfig.read(myPlatformFacade, configFile);
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

  protected void notifyListeners() {
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

  @Override
  public String toLogString() {
    return String.format("GitRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s, myRootDir=%s}",
                         myCurrentBranch, myCurrentRevision, myState, myRootDir);
  }

  @Override
  public String toString() {
    return getPresentableUrl();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitRepositoryImpl that = (GitRepositoryImpl)o;

    if (myProject != null ? !myProject.equals(that.myProject) : that.myProject != null) return false;
    if (myRootDir != null ? !myRootDir.equals(that.myRootDir) : that.myRootDir != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProject != null ? myProject.hashCode() : 0;
    result = 31 * result + (myRootDir != null ? myRootDir.hashCode() : 0);
    return result;
  }
}
