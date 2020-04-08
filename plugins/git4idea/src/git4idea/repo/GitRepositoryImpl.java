// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.dvcs.ignore.IgnoredToExcludedSynchronizer;
import com.intellij.dvcs.ignore.VcsIgnoredHolderUpdateListener;
import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangesViewI;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.util.StopWatch;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchesCollection;
import git4idea.commands.Git;
import git4idea.ignore.GitRepositoryIgnoredFilesHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.util.ObjectUtils.notNull;

public class GitRepositoryImpl extends RepositoryImpl implements GitRepository {
  private static final Logger LOG = Logger.getInstance(GitRepositoryImpl.class);

  @NotNull private final GitVcs myVcs;
  @NotNull private final GitRepositoryReader myReader;
  @NotNull private final VirtualFile myGitDir;
  @NotNull private final GitRepositoryFiles myRepositoryFiles;

  @Nullable private final GitUntrackedFilesHolder myUntrackedFilesHolder;
  @Nullable private final GitRepositoryIgnoredFilesHolder myIgnoredRepositoryFilesHolder;
  @Nullable private final GitConflictsHolder myConflictsHolder;

  @NotNull private volatile GitRepoInfo myInfo;

  private GitRepositoryImpl(@NotNull VirtualFile rootDir,
                            @NotNull VirtualFile gitDir,
                            @NotNull Project project,
                            @NotNull Disposable parentDisposable,
                            final boolean light) {
    super(project, rootDir, parentDisposable);
    myVcs = GitVcs.getInstance(project);
    myGitDir = gitDir;
    myRepositoryFiles = GitRepositoryFiles.getInstance(gitDir);
    myReader = new GitRepositoryReader(myRepositoryFiles);
    myInfo = readRepoInfo();

    if (!light) {
      myUntrackedFilesHolder = new GitUntrackedFilesHolder(this, myRepositoryFiles);
      Disposer.register(this, myUntrackedFilesHolder);

      myIgnoredRepositoryFilesHolder =
        new GitRepositoryIgnoredFilesHolder(project, this, GitRepositoryManager.getInstance(project), Git.getInstance());
      Disposer.register(this, myIgnoredRepositoryFilesHolder);
      myIgnoredRepositoryFilesHolder.addUpdateStateListener(new MyRepositoryIgnoredHolderUpdateListener(project));
      myIgnoredRepositoryFilesHolder.addUpdateStateListener(new IgnoredToExcludedSynchronizer(project, this));

      myConflictsHolder = new GitConflictsHolder(this);
      Disposer.register(this, myConflictsHolder);
    }
    else {
      myUntrackedFilesHolder = null;
      myIgnoredRepositoryFilesHolder = null;
      myConflictsHolder = null;
    }
  }

  /**
   * @deprecated Use {@link GitRepositoryManager#getRepositoryForRoot} to obtain an instance of a Git repository.
   */
  @NotNull
  @Deprecated
  public static GitRepository getInstance(@NotNull VirtualFile root,
                                          @NotNull Project project,
                                          boolean listenToRepoChanges) {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    return notNull(repository, () -> createInstance(root, project, project, listenToRepoChanges));
  }

  /**
   * Creates a new instance of the GitRepository for the given Git root directory. <br/>
   * Use {@link GitRepositoryManager#getRepositoryForRoot(VirtualFile)} if you need to obtain an instance
   */
  @ApiStatus.Internal
  @NotNull
  public static GitRepository createInstance(@NotNull VirtualFile root,
                                             @NotNull Project project,
                                             @NotNull Disposable parentDisposable,
                                             boolean listenToRepoChanges) {
    return createInstance(root, Objects.requireNonNull(GitUtil.findGitDir(root)), project, parentDisposable, listenToRepoChanges);
  }

  @ApiStatus.Internal
  @NotNull
  static GitRepository createInstance(@NotNull VirtualFile root,
                                      @NotNull VirtualFile gitDir,
                                      @NotNull Project project,
                                      @NotNull Disposable parentDisposable,
                                      boolean listenToRepoChanges) {
    GitRepositoryImpl repository = new GitRepositoryImpl(root, gitDir, project, parentDisposable, !listenToRepoChanges);
    if (listenToRepoChanges) {
      repository.getUntrackedFilesHolder().setupVfsListener(project);
      repository.getIgnoredFilesHolder().setupListeners();
      repository.setupUpdater();
      GitRepositoryManager.getInstance(project).notifyListenersAsync(repository);
    }
    return repository;
  }

  private void setupUpdater() {
    GitRepositoryUpdater updater = new GitRepositoryUpdater(this, myRepositoryFiles);
    Disposer.register(this, updater);
    if (myIgnoredRepositoryFilesHolder != null) {
      myIgnoredRepositoryFilesHolder.startRescan();
    }
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

  @NotNull
  @Override
  public GitConflictsHolder getConflictsHolder() {
    if (myConflictsHolder == null) {
      throw new IllegalStateException("Using conflicts holder with light git repository instance " + this);
    }
    return myConflictsHolder;
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

  @Nullable
  @Override
  public GitBranchTrackInfo getBranchTrackInfo(@NotNull String localBranchName) {
    return myInfo.getBranchTrackInfosMap().get(localBranchName);
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
  public void update() {
    if (ApplicationManager.getApplication().isDispatchThread() && !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("Reading Git repository information should not be done on the EDT");
    }
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
    boolean isShallow = myReader.hasShallowCommits();
    Collection<GitBranchTrackInfo> trackInfos =
      config.parseTrackInfos(state.getLocalBranches().keySet(), state.getRemoteBranches().keySet());
    GitHooksInfo hooksInfo = myReader.readHooksInfo();
    Collection<GitSubmoduleInfo> submodules = new GitModulesFileReader().read(getSubmoduleFile());
    sw.report(LOG);
    return new GitRepoInfo(state.getCurrentBranch(), state.getCurrentRevision(), state.getState(), new LinkedHashSet<>(remotes),
                           new HashMap<>(state.getLocalBranches()), new HashMap<>(state.getRemoteBranches()),
                           new LinkedHashSet<>(trackInfos),
                           submodules, hooksInfo, isShallow);
  }

  @NotNull
  private File getSubmoduleFile() {
    return new File(VfsUtilCore.virtualToIoFile(getRoot()), ".gitmodules");
  }

  private static void notifyIfRepoChanged(@NotNull GitRepository repository,
                                          @NotNull GitRepoInfo previousInfo,
                                          @NotNull GitRepoInfo info) {
    Project project = repository.getProject();
    if (!project.isDisposed() && !info.equals(previousInfo)) {
      GitRepositoryManager.getInstance(project).notifyListenersAsync(repository);
    }
  }

  @NotNull
  @Override
  public String toLogString() {
    return "GitRepository " + getRoot() + " : " + myInfo;
  }

  @NotNull
  @Override
  public GitRepositoryIgnoredFilesHolder getIgnoredFilesHolder() {
    if (myIgnoredRepositoryFilesHolder == null) throw new UnsupportedOperationException("Unsupported for light Git repository");
    return myIgnoredRepositoryFilesHolder;
  }

  private static class MyRepositoryIgnoredHolderUpdateListener implements VcsIgnoredHolderUpdateListener {
    @NotNull private final ChangesViewI myChangesViewI;
    @NotNull private final Project myProject;

    MyRepositoryIgnoredHolderUpdateListener(@NotNull Project project) {
      myChangesViewI = ChangesViewManager.getInstance(project);
      myProject = project;
    }

    @Override
    public void updateStarted() {
      myChangesViewI.scheduleRefresh(); //TODO optimize: remove additional refresh
    }

    @Override
    public void updateFinished(@NotNull Collection<FilePath> ignoredPaths, boolean isFullRescan) {
      if(myProject.isDisposed()) return;

      myChangesViewI.scheduleRefresh();
    }
  }
}
