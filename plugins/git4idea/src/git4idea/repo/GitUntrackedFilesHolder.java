// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.ignore.IgnoredToExcludedSynchronizer;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl;
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder;
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRefreshUsageCollector;
import git4idea.ignore.GitRepositoryIgnoredFilesHolder;
import git4idea.index.GitIndexStatusUtilKt;
import git4idea.index.LightFileStatus.StatusRecord;
import git4idea.status.GitRefreshListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GitUntrackedFilesHolder implements Disposable {
  private static final Logger LOG = Logger.getInstance(GitUntrackedFilesHolder.class);

  private final Project myProject;
  private final VirtualFile myRoot;
  private final GitRepository myRepository;

  private final Set<FilePath> myUntrackedFiles = new HashSet<>();
  private final Set<FilePath> myDirtyFiles = new HashSet<>();
  private boolean myEverythingDirty = true;

  private final MergingUpdateQueue myQueue;
  private final Object LOCK = new Object();
  private boolean myInUpdate = false;

  private final MyGitRepositoryIgnoredFilesHolder myIgnoredFilesHolder;

  GitUntrackedFilesHolder(@NotNull GitRepository repository) {
    myRepository = repository;
    myProject = repository.getProject();
    myRoot = repository.getRoot();

    myIgnoredFilesHolder = new MyGitRepositoryIgnoredFilesHolder();
    myQueue = VcsIgnoreManagerImpl.getInstanceImpl(myProject).getIgnoreRefreshQueue();

    scheduleUpdate();
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myUntrackedFiles.clear();
      myIgnoredFilesHolder.clear();
      myDirtyFiles.clear();
    }
  }

  /**
   * Adds the file to the list of untracked.
   */
  public void addUntracked(@NotNull FilePath file) {
    addUntracked(Collections.singletonList(file));
  }

  /**
   * Adds several files to the list of untracked.
   */
  public void addUntracked(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      myUntrackedFiles.addAll(files);
      if (!myEverythingDirty) myDirtyFiles.addAll(files);
    }
    ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();
    scheduleUpdate();
  }

  /**
   * Removes several files from untracked.
   */
  public void removeUntracked(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      files.forEach(myUntrackedFiles::remove);
      if (!myEverythingDirty) myDirtyFiles.addAll(files);
    }
    ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();
    scheduleUpdate();
  }

  /**
   * Marks files as possibly untracked to be checked on the next {@link #update()} call.
   *
   * @param files files that are possibly untracked.
   */
  public void markPossiblyUntracked(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      if (myEverythingDirty) return;
      for (FilePath filePath : files) {
        if (myIgnoredFilesHolder.ignoredFiles.containsExplicitly(filePath) ||
            !myIgnoredFilesHolder.ignoredFiles.hasAncestor(filePath)) {
        myDirtyFiles.add(filePath);
        }
      }
    }
    scheduleUpdate();
  }

  /**
   * Returns the list of unversioned files.
   * This method may be slow, if the full-refresh of untracked files is needed.
   *
   * @return untracked files.
   * @throws VcsException if there is an unexpected error during Git execution.
   * @deprecated use {@link #retrieveUntrackedFilePaths} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Collection<VirtualFile> retrieveUntrackedFiles() throws VcsException {
    return ContainerUtil.mapNotNull(retrieveUntrackedFilePaths(), FilePath::getVirtualFile);
  }

  public void invalidate() {
    synchronized (LOCK) {
      myEverythingDirty = true;
      myDirtyFiles.clear();
    }
    scheduleUpdate();
  }

  public boolean isInUpdateMode() {
    synchronized (LOCK) {
      return myInUpdate;
    }
  }

  public @NotNull Set<FilePath> getUntrackedFilePaths() {
    synchronized (LOCK) {
      return new HashSet<>(myUntrackedFiles);
    }
  }

  public boolean containsUntrackedFile(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myUntrackedFiles.contains(filePath);
    }
  }

  public @NotNull Collection<FilePath> retrieveUntrackedFilePaths() throws VcsException {
    VcsIgnoreManagerImpl.getInstanceImpl(myProject).awaitRefreshQueue();
    return getUntrackedFilePaths();
  }

  @NotNull GitRepositoryIgnoredFilesHolder getIgnoredFilesHolder() {
    return myIgnoredFilesHolder;
  }

  private boolean isDirty() {
    synchronized (LOCK) {
      return myEverythingDirty || !myDirtyFiles.isEmpty();
    }
  }

  private void scheduleUpdate() {
    synchronized (LOCK) {
      if (!isDirty()) return;
      myInUpdate = true;
    }
    BackgroundTaskUtil.syncPublisher(myProject, VcsManagedFilesHolder.TOPIC).updatingModeChanged();
    myQueue.queue(DisposableUpdate.createDisposable(this, new ComparableObject.Impl(this, "update"), this::update));
  }

  /**
   * Queries Git to check the status of {@code myPossiblyUntrackedFiles} and moves them to {@code myDefinitelyUntrackedFiles}.
   */
  private void update() {
    boolean nothingToDo;
    @Nullable List<FilePath> dirtyFiles;
    synchronized (LOCK) {
      nothingToDo = !isDirty();
      if (nothingToDo) myInUpdate = false;

      dirtyFiles = myEverythingDirty ? null : new ArrayList<>(myDirtyFiles);
      myDirtyFiles.clear();
      myEverythingDirty = false;
    }
    if (nothingToDo) {
      BackgroundTaskUtil.syncPublisher(myProject, VcsManagedFilesHolder.TOPIC).updatingModeChanged();
      return;
    }

    BackgroundTaskUtil.syncPublisher(myProject, GitRefreshListener.TOPIC).progressStarted();
    try {
      boolean everythingDirty = dirtyFiles == null || dirtyFiles.contains(VcsUtil.getFilePath(myRoot));
      StructuredIdeActivity activity = GitRefreshUsageCollector.logUntrackedRefresh(myProject, everythingDirty);
      RefreshResult result = refreshFiles(dirtyFiles);
      activity.finished();

      removePathsUnderOtherRoots(result.untracked, "unversioned");
      removePathsUnderOtherRoots(result.ignored, "ignored");

      RecursiveFilePathSet dirtyScope = null;
      if (dirtyFiles != null) {
        dirtyScope = new RecursiveFilePathSet(myRoot.isCaseSensitive());
        dirtyScope.addAll(dirtyFiles);
      }

      Set<FilePath> oldIgnored;
      Set<FilePath> newIgnored;
      synchronized (LOCK) {
        oldIgnored = myIgnoredFilesHolder.getIgnoredFilePaths();
        applyRefreshResult(result, dirtyScope, oldIgnored);
        newIgnored = myIgnoredFilesHolder.getIgnoredFilePaths();

        myInUpdate = isDirty();
      }

      BackgroundTaskUtil.syncPublisher(myProject, GitRefreshListener.TOPIC).repositoryUpdated(myRepository);
      BackgroundTaskUtil.syncPublisher(myProject, VcsManagedFilesHolder.TOPIC).updatingModeChanged();
      ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();

      myProject.getService(IgnoredToExcludedSynchronizer.class).onIgnoredFilesUpdate(newIgnored, oldIgnored);
    }
    finally {
      BackgroundTaskUtil.syncPublisher(myProject, GitRefreshListener.TOPIC).progressStopped();
    }
  }

  private void applyRefreshResult(@NotNull RefreshResult result,
                                  @Nullable RecursiveFilePathSet dirtyScope,
                                  @NotNull Set<FilePath> oldIgnored) {
    RecursiveFilePathSet newIgnored = new RecursiveFilePathSet(myRoot.isCaseSensitive());

    if (dirtyScope != null) {
      myUntrackedFiles.removeIf(filePath -> dirtyScope.hasAncestor(filePath));
      myUntrackedFiles.addAll(result.untracked);

      for (FilePath filePath : oldIgnored) {
        if (!dirtyScope.hasAncestor(filePath)) {
          newIgnored.add(filePath);
        }
      }
      for (FilePath filePath : result.ignored) {
        if (!newIgnored.hasAncestor(filePath)) { // prevent storing both parent and child directories
          newIgnored.add(filePath);
        }
      }
    }
    else {
      myUntrackedFiles.clear();
      myUntrackedFiles.addAll(result.untracked);
      newIgnored.addAll(result.ignored);
    }

    myIgnoredFilesHolder.setIgnoredFiles(newIgnored);
  }

  /**
   * @see git4idea.status.GitStagingAreaHolder#removeUnwantedRecords
   */
  private void removePathsUnderOtherRoots(@NotNull Collection<FilePath> untrackedFiles, @NonNls String type) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

    int removedFiles = 0;
    int maxFilesToReport = 10;

    Iterator<FilePath> it = untrackedFiles.iterator();
    while (it.hasNext()) {
      FilePath filePath = it.next();
      VirtualFile root = vcsManager.getVcsRootFor(filePath);
      if (myRoot.equals(root)) continue;

      it.remove();
      removedFiles++;
      if (removedFiles < maxFilesToReport) {
        LOG.debug(String.format("Ignoring %s file under another root: %s; root: %s; mapped root: %s",
                                type, filePath.getPresentableUrl(), myRoot.getPresentableUrl(),
                                root != null ? root.getPresentableUrl() : "null"));
      }
    }
    if (removedFiles >= maxFilesToReport) {
      LOG.debug(String.format("Ignoring %s files under another root: %s files total", type, removedFiles));
    }
  }


  private @NotNull RefreshResult refreshFiles(@Nullable List<FilePath> dirty) {
    try {
      boolean withIgnored = AdvancedSettings.getBoolean("vcs.process.ignored");
      List<StatusRecord> fileStatuses = GitIndexStatusUtilKt.getFileStatus(myProject, myRoot, ContainerUtil.notNullize(dirty),
                                                                           false, true, withIgnored);

      RefreshResult result = new RefreshResult();
      for (StatusRecord status : fileStatuses) {
        if (GitIndexStatusUtilKt.isUntracked(status.getIndex())) {
          result.untracked.add(getFilePath(myRoot, status));
        }
        if (GitIndexStatusUtilKt.isIgnored(status.getIndex())) {
          result.ignored.add(getFilePath(myRoot, status));
        }
      }
      return result;
    }
    catch (VcsException e) {
      LOG.warn(e);
      return new RefreshResult();
    }
  }

  private class MyGitRepositoryIgnoredFilesHolder implements GitRepositoryIgnoredFilesHolder {
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") // underlying collection is immutable
    private volatile RecursiveFilePathSet ignoredFiles = new RecursiveFilePathSet(myRoot.isCaseSensitive());
    private boolean initialized = false;

    @Override
    public boolean getInitialized() {
      return initialized;
    }

    @Override
    public @NotNull Set<FilePath> getIgnoredFilePaths() {
      return new HashSet<>(ignoredFiles.filePaths());
    }

    @Override
    public boolean isInUpdateMode() {
      return GitUntrackedFilesHolder.this.isInUpdateMode();
    }

    @Override
    public boolean containsFile(@NotNull FilePath file) {
      return ignoredFiles.hasAncestor(file);
    }

    @Override
    public void removeIgnoredFiles(@NotNull Collection<? extends FilePath> filePaths) {
      synchronized (LOCK) {
        ignoredFiles = prepareIgnoredSetExcludingPaths(new HashSet<>(filePaths));

        if (!myEverythingDirty) {
          // break parent ignored directory into separate ignored files
          if (ContainerUtil.exists(filePaths, ignoredFiles::hasAncestor)) {
            myEverythingDirty = true;
          }
          else {
            myDirtyFiles.addAll(filePaths);
          }
        }
      }
      ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();
      scheduleUpdate();
    }

    private @NotNull RecursiveFilePathSet prepareIgnoredSetExcludingPaths(@NotNull Set<? extends FilePath> pathsToExclude) {
      RecursiveFilePathSet newIgnoredFiles = new RecursiveFilePathSet(myRoot.isCaseSensitive());
      for (FilePath ignoredFile : ignoredFiles.filePaths()) {
        if (!pathsToExclude.contains(ignoredFile)) {
          newIgnoredFiles.add(ignoredFile);
        }
      }
      return newIgnoredFiles;
    }

    private void setIgnoredFiles(RecursiveFilePathSet ignoredFiles) {
      this.ignoredFiles = ignoredFiles;
      initialized = true;
    }

    private void clear() {
      ignoredFiles = new RecursiveFilePathSet(myRoot.isCaseSensitive());
    }
  }

  private static @NotNull FilePath getFilePath(@NotNull VirtualFile root, @NotNull StatusRecord status) {
    String path = status.getPath();
    return GitContentRevision.createPath(root, path, path.endsWith("/"));
  }

  private static class RefreshResult {
    public final @NotNull Set<FilePath> untracked = new HashSet<>();
    public final @NotNull Set<FilePath> ignored = new HashSet<>();
  }


  @TestOnly
  public Waiter createWaiter() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return new Waiter(myQueue);
  }

  @TestOnly
  public static class Waiter {
    private final MergingUpdateQueue myQueue;

    public Waiter(@NotNull MergingUpdateQueue queue) {
      myQueue = queue;
    }

    public void waitFor() {
      CountDownLatch waiter = new CountDownLatch(1);
      myQueue.queue(Update.create(waiter, () -> waiter.countDown()));
      ProgressIndicatorUtils.awaitWithCheckCanceled(waiter);
      try {
        myQueue.waitForAllExecuted(10, TimeUnit.SECONDS);
      }
      catch (TimeoutException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
