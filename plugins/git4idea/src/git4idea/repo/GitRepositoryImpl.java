// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsScopeKt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.vcs.log.Hash;
import git4idea.GitDisposable;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchesCollection;
import git4idea.ignore.GitRepositoryIgnoredFilesHolder;
import git4idea.status.GitStagingAreaHolder;
import git4idea.telemetry.GitTelemetrySpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.computeWithSpan;
import static com.intellij.util.ObjectUtils.notNull;
import static git4idea.repo.GitRecentCheckoutBranches.collectRecentCheckoutBranches;

public final class GitRepositoryImpl extends RepositoryImpl implements GitRepository {
  private static final Logger LOG = Logger.getInstance(GitRepositoryImpl.class);

  private final @NotNull GitVcs myVcs;
  private final @NotNull GitRepositoryReader myReader;
  private final @NotNull VirtualFile myGitDir;
  private final @NotNull GitRepositoryFiles myRepositoryFiles;

  private final @NotNull GitUntrackedFilesHolder myUntrackedFilesHolder;
  private final @NotNull GitStagingAreaHolder myStagingAreaHolder;
  private final @NotNull GitRepositoryIgnoredFilesHolder myIgnoredRepositoryFilesHolder;

  private volatile @NotNull GitRepoInfo myInfo;
  private volatile @NotNull List<GitLocalBranch> myRecentCheckoutBranches = Collections.emptyList();

  /**
   * @param rootDir Root of the repository (parent directory of '.git' file/directory).
   * @param gitDir  '.git' directory location. For worktrees - location of the 'main_repo/.git/worktrees/worktree_name/'.
   */
  private GitRepositoryImpl(@NotNull VirtualFile rootDir,
                            @NotNull VirtualFile gitDir,
                            @NotNull Project project,
                            @NotNull Disposable parentDisposable) {
    super(project, rootDir, parentDisposable);
    myVcs = GitVcs.getInstance(project);
    myGitDir = gitDir;
    myRepositoryFiles = GitRepositoryFiles.createInstance(rootDir, gitDir);
    myReader = new GitRepositoryReader(myRepositoryFiles);
    myInfo = readRepoInfo();

    myStagingAreaHolder = new GitStagingAreaHolder(this);

    myUntrackedFilesHolder = new GitUntrackedFilesHolder(this);
    Disposer.register(this, myUntrackedFilesHolder);

    myIgnoredRepositoryFilesHolder = new GitRepositoryIgnoredFilesHolder(this);
  }

  /**
   * @deprecated Use {@link GitRepositoryManager#getRepositoryForRoot} to obtain an instance of a Git repository.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull GitRepository getInstance(@NotNull VirtualFile root,
                                          @NotNull Project project,
                                          boolean listenToRepoChanges) {
    GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    return notNull(repository, () -> createInstance(root, project, GitDisposable.getInstance(project)));
  }

  /**
   * @deprecated Use {@link #createInstance(VirtualFile, Project, Disposable)}
   */
  @Deprecated
  @ApiStatus.Internal
  public static @NotNull GitRepository createInstance(@NotNull VirtualFile root,
                                             @NotNull Project project,
                                             @NotNull Disposable parentDisposable,
                                             boolean listenToRepoChanges) {
    return createInstance(root, project, parentDisposable);
  }

  /**
   * Creates a new instance of the GitRepository for the given Git root directory. <br/>
   * Use {@link GitRepositoryManager#getRepositoryForRoot(VirtualFile)} if you need to obtain an instance
   */
  @ApiStatus.Internal
  public static @NotNull GitRepository createInstance(@NotNull VirtualFile root,
                                             @NotNull Project project,
                                             @NotNull Disposable parentDisposable) {
    VirtualFile gitDir = Objects.requireNonNull(GitUtil.findGitDir(root));
    return createInstance(root, gitDir, project, parentDisposable);
  }

  @ApiStatus.Internal
  static @NotNull GitRepository createInstance(@NotNull VirtualFile root,
                                               @NotNull VirtualFile gitDir,
                                               @NotNull Project project,
                                               @NotNull Disposable parentDisposable) {
    ProgressManager.checkCanceled();
    GitRepositoryImpl repository = new GitRepositoryImpl(root, gitDir, project, parentDisposable);
    repository.setupUpdater();
    GitRepositoryManager.getInstance(project).notifyListenersAsync(repository);
    return repository;
  }

  private void setupUpdater() {
    GitRepositoryUpdater updater = new GitRepositoryUpdater(this, myRepositoryFiles);
    Disposer.register(this, updater);
  }

  @Deprecated
  @Override
  public @NotNull VirtualFile getGitDir() {
    return myGitDir;
  }

  @Override
  public @NotNull GitRepositoryFiles getRepositoryFiles() {
    return myRepositoryFiles;
  }

  @Override
  public @NotNull GitStagingAreaHolder getStagingAreaHolder() {
    return myStagingAreaHolder;
  }

  @Override
  public @NotNull GitUntrackedFilesHolder getUntrackedFilesHolder() {
    return myUntrackedFilesHolder;
  }

  @Override
  public @NotNull GitRepositoryIgnoredFilesHolder getIgnoredFilesHolder() {
    return myIgnoredRepositoryFilesHolder;
  }

  @Override
  public @NotNull GitRepoInfo getInfo() {
    return myInfo;
  }

  @Override
  public @Nullable GitLocalBranch getCurrentBranch() {
    return myInfo.getCurrentBranch();
  }

  @Override
  public @Nullable String getCurrentRevision() {
    return myInfo.getCurrentRevision();
  }

  @Override
  public @NotNull State getState() {
    return myInfo.getState();
  }

  @Override
  public @Nullable String getCurrentBranchName() {
    GitLocalBranch currentBranch = getCurrentBranch();
    return currentBranch == null ? null : currentBranch.getName();
  }

  @Override
  public @NotNull GitVcs getVcs() {
    return myVcs;
  }

  @Override
  public @NotNull Collection<GitSubmoduleInfo> getSubmodules() {
    return myInfo.getSubmodules();
  }

  /**
   * @return local and remote branches in this repository.
   */
  @Override
  public @NotNull GitBranchesCollection getBranches() {
    GitRepoInfo info = myInfo;
    return new GitBranchesCollection(info.getLocalBranchesWithHashes(), info.getRemoteBranchesWithHashes(), myRecentCheckoutBranches);
  }

  @Override
  public @NotNull Collection<GitRemote> getRemotes() {
    return myInfo.getRemotes();
  }

  @Override
  public @NotNull Collection<GitBranchTrackInfo> getBranchTrackInfos() {
    return myInfo.getBranchTrackInfos();
  }

  @Override
  public @Nullable GitBranchTrackInfo getBranchTrackInfo(@NotNull String localBranchName) {
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
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    GitRepoInfo previousInfo = myInfo;
    myInfo = readRepoInfo();
    notifyIfRepoChanged(this, previousInfo, myInfo);
  }

  private @NotNull GitRepoInfo readRepoInfo() {
    return computeWithSpan(TelemetryManager.getInstance().getTracer(VcsScopeKt.VcsScope),
                           GitTelemetrySpan.Repository.ReadGitRepositoryInfo.getName(), span -> {
      span.setAttribute("repository", getShortRepositoryName(this));

      File configFile = myRepositoryFiles.getConfigFile();
      GitConfig config = GitConfig.read(configFile);
      myRepositoryFiles.updateCustomPaths(config.parseCore());

      Collection<GitRemote> remotes = config.parseRemotes();
      GitBranchState state = myReader.readState(remotes);
      boolean isShallow = myReader.hasShallowCommits();
      Collection<GitBranchTrackInfo> trackInfos =
        config.parseTrackInfos(state.getLocalBranches().keySet(), state.getRemoteBranches().keySet());
      GitHooksInfo hooksInfo = myReader.readHooksInfo();
      Collection<GitSubmoduleInfo> submodules = new GitModulesFileReader().read(getSubmoduleFile());
      Map<GitLocalBranch, Hash> localBranches = new HashMap<>(state.getLocalBranches());
      myRecentCheckoutBranches = collectRecentCheckoutBranches(this, branch -> localBranches.containsKey(branch));
      return new GitRepoInfo(state.getCurrentBranch(), state.getCurrentRevision(), state.getState(), new LinkedHashSet<>(remotes),
                             localBranches, new HashMap<>(state.getRemoteBranches()),
                             new LinkedHashSet<>(trackInfos),
                             submodules, hooksInfo, isShallow);
    });
  }

  private @NotNull File getSubmoduleFile() {
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

  @Override
  public @NotNull String toLogString() {
    return "GitRepository " + getRoot() + " : " + myInfo;
  }
}
