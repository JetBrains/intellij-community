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

import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.util.StopWatch;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchesCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.progress.util.BackgroundTaskUtil.syncPublisher;
import static com.intellij.util.ObjectUtils.assertNotNull;

public class GitRepositoryImpl extends RepositoryImpl implements GitRepository {

  @NotNull private final GitVcs myVcs;
  @NotNull private final GitRepositoryReader myReader;
  @NotNull private final VirtualFile myGitDir;
  @NotNull private final GitRepositoryFiles myRepositoryFiles;

  @Nullable private final GitUntrackedFilesHolder myUntrackedFilesHolder;

  @NotNull private volatile GitRepoInfo myInfo;

  private GitRepositoryImpl(@NotNull VirtualFile rootDir,
                            @NotNull VirtualFile gitDir,
                            @NotNull Project project,
                            @NotNull Disposable parentDisposable,
                            final boolean light) {
    super(project, rootDir, parentDisposable);
    myVcs = assertNotNull(GitVcs.getInstance(project));
    myGitDir = gitDir;
    myRepositoryFiles = GitRepositoryFiles.getInstance(gitDir);
    myReader = new GitRepositoryReader(myRepositoryFiles);
    myInfo = readRepoInfo();
    if (!light) {
      myUntrackedFilesHolder = new GitUntrackedFilesHolder(this, myRepositoryFiles);
      Disposer.register(this, myUntrackedFilesHolder);
    }
    else {
      myUntrackedFilesHolder = null;
    }
  }

  @NotNull
  public static GitRepository getInstance(@NotNull VirtualFile root,
                                          @NotNull Project project,
                                          boolean listenToRepoChanges) {
    return getInstance(root, assertNotNull(GitUtil.findGitDir(root)), project, listenToRepoChanges);
  }

  @NotNull
  public static GitRepository getInstance(@NotNull VirtualFile root,
                                          @NotNull VirtualFile gitDir,
                                          @NotNull Project project,
                                          boolean listenToRepoChanges) {
    GitRepositoryImpl repository = new GitRepositoryImpl(root, gitDir, project, project, !listenToRepoChanges);
    if (listenToRepoChanges) {
      repository.getUntrackedFilesHolder().setupVfsListener(project);
      repository.setupUpdater();
      notifyListenersAsync(repository);
    }
    return repository;
  }

  private void setupUpdater() {
    GitRepositoryUpdater updater = new GitRepositoryUpdater(this, myRepositoryFiles);
    Disposer.register(this, updater);
  }

  @Deprecated
  @NotNull
  @Override
  public VirtualFile getGitDir() {
    return myGitDir;
  }

  @NotNull
  @Override
  public GitRepositoryFiles getRepositoryFiles() {
    return myRepositoryFiles;
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
  public GitRepoInfo getInfo() {
    return myInfo;
  }

  @Override
  @Nullable
  public GitLocalBranch getCurrentBranch() {
    return myInfo.getCurrentBranch();
  }

  @Nullable
  @Override
  public String getCurrentRevision() {
    return myInfo.getCurrentRevision();
  }

  @NotNull
  @Override
  public State getState() {
    return myInfo.getState();
  }

  @Nullable
  @Override
  public String getCurrentBranchName() {
    GitLocalBranch currentBranch = getCurrentBranch();
    return currentBranch == null ? null : currentBranch.getName();
  }

  @NotNull
  @Override
  public GitVcs getVcs() {
    return myVcs;
  }

  @NotNull
  @Override
  public Collection<GitSubmoduleInfo> getSubmodules() {
    return myInfo.getSubmodules();
  }

  /**
   * @return local and remote branches in this repository.
   */
  @Override
  @NotNull
  public GitBranchesCollection getBranches() {
    GitRepoInfo info = myInfo;
    return new GitBranchesCollection(info.getLocalBranchesWithHashes(), info.getRemoteBranchesWithHashes());
  }

  @Override
  @NotNull
  public Collection<GitRemote> getRemotes() {
    return myInfo.getRemotes();
  }

  @Override
  @NotNull
  public Collection<GitBranchTrackInfo> getBranchTrackInfos() {
    return myInfo.getBranchTrackInfos();
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
  public void update() {
    GitRepoInfo previousInfo = myInfo;
    myInfo = readRepoInfo();
    notifyIfRepoChanged(this, previousInfo, myInfo);
  }

  @NotNull
  private GitRepoInfo readRepoInfo() {
    StopWatch sw = StopWatch.start("Reading Git repo info in " + getShortRepositoryName(this));
    File configFile = myRepositoryFiles.getConfigFile();
    GitConfig config = GitConfig.read(configFile);
    Collection<GitRemote> remotes = config.parseRemotes();
    GitBranchState state = myReader.readState(remotes);
    Collection<GitBranchTrackInfo> trackInfos = config.parseTrackInfos(state.getLocalBranches().keySet(), state.getRemoteBranches().keySet());
    GitHooksInfo hooksInfo = myReader.readHooksInfo();
    Collection<GitSubmoduleInfo> submodules = new GitModulesFileReader().read(getSubmoduleFile());
    sw.report();
    return new GitRepoInfo(state.getCurrentBranch(), state.getCurrentRevision(), state.getState(), remotes,
                           state.getLocalBranches(), state.getRemoteBranches(), trackInfos, submodules, hooksInfo);
  }

  @NotNull
  private File getSubmoduleFile() {
    return new File(VfsUtilCore.virtualToIoFile(getRoot()), ".gitmodules");
  }

  private static void notifyIfRepoChanged(@NotNull final GitRepository repository, @NotNull GitRepoInfo previousInfo, @NotNull GitRepoInfo info) {
    if (!repository.getProject().isDisposed() && !info.equals(previousInfo)) {
      notifyListenersAsync(repository);
    }
  }

  private static void notifyListenersAsync(@NotNull GitRepository repository) {
    Runnable task = () -> {
      syncPublisher(repository.getProject(), GIT_REPO_CHANGE).repositoryChanged(repository);
    };
    BackgroundTaskUtil.executeOnPooledThread(task, repository);
  }

  @NotNull
  @Override
  public String toLogString() {
    return "GitRepository " + getRoot() + " : " + myInfo;
  }
}
