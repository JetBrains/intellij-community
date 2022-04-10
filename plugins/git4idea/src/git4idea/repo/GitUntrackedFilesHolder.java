// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.impl.projectlevelman.RecursiveFilePathSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRefreshUsageCollector;
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

public class GitUntrackedFilesHolder implements Disposable {
  private static final Logger LOG = Logger.getInstance(GitUntrackedFilesHolder.class);

  private final Project myProject;
  private final VirtualFile myRoot;
  private final GitRepository myRepository;

  private final Set<FilePath> myUntrackedFiles = new HashSet<>();
  private final RecursiveFilePathSet myIgnoredFiles;
  private final Set<FilePath> myDirtyFiles = new HashSet<>();
  private boolean myEverythingDirty = true;

  private final MergingUpdateQueue myQueue;
  private final Object LOCK = new Object();
  private boolean myInUpdate = false;

  GitUntrackedFilesHolder(@NotNull GitRepository repository) {
    myRepository = repository;
    myProject = repository.getProject();
    myRoot = repository.getRoot();

    myIgnoredFiles = new RecursiveFilePathSet(myRoot.isCaseSensitive());
    myQueue = VcsIgnoreManagerImpl.getInstanceImpl(myProject).getIgnoreRefreshQueue();

    scheduleUpdate();
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myUntrackedFiles.clear();
      myIgnoredFiles.clear();
      myDirtyFiles.clear();
    }
  }

  /**
   * Adds the file to the list of untracked.
   */
  public void add(@NotNull FilePath file) {
    add(Collections.singletonList(file));
  }

  /**
   * Adds several files to the list of untracked.
   */
  public void add(@NotNull Collection<? extends FilePath> files) {
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
  public void remove(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      files.forEach(myUntrackedFiles::remove);
      if (!myEverythingDirty) myDirtyFiles.addAll(files);
    }
    ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();
    scheduleUpdate();
  }

  public void removeIgnored(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      files.forEach(myIgnoredFiles::remove);
      if (!myEverythingDirty) {
        // break parent ignored directory into separate ignored files
        if (ContainerUtil.exists(files, myIgnoredFiles::hasAncestor)) {
          myEverythingDirty = true;
        }
        else {
          myDirtyFiles.addAll(files);
        }
      }
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
        if (myIgnoredFiles.contains(filePath) ||
            !myIgnoredFiles.hasAncestor(filePath)) {
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
  @NotNull
  public Collection<VirtualFile> retrieveUntrackedFiles() throws VcsException {
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

  @NotNull
  public Collection<FilePath> getUntrackedFilePaths() {
    synchronized (LOCK) {
      return new ArrayList<>(myUntrackedFiles);
    }
  }

  @NotNull
  public Collection<FilePath> getIgnoredFilePaths() {
    synchronized (LOCK) {
      return new ArrayList<>(myIgnoredFiles.filePaths());
    }
  }

  public boolean containsFile(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myUntrackedFiles.contains(filePath);
    }
  }

  public boolean containsIgnoredFile(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myIgnoredFiles.hasAncestor(filePath);
    }
  }

  @NotNull
  public Collection<FilePath> retrieveUntrackedFilePaths() throws VcsException {
    VcsIgnoreManagerImpl.getInstanceImpl(myProject).awaitRefreshQueue();
    return getUntrackedFilePaths();
  }

  @NotNull
  public Collection<FilePath> retrieveIgnoredFilePaths() {
    VcsIgnoreManagerImpl.getInstanceImpl(myProject).awaitRefreshQueue();
    return getIgnoredFilePaths();
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
      List<FilePath> newIgnored;
      synchronized (LOCK) {
        oldIgnored = new HashSet<>(myIgnoredFiles.filePaths());
        applyRefreshResult(result, dirtyScope, oldIgnored);
        newIgnored = new ArrayList<>(myIgnoredFiles.filePaths());

        myInUpdate = isDirty();
      }

      BackgroundTaskUtil.syncPublisher(myProject, GitRefreshListener.TOPIC).repositoryUpdated(myRepository);
      BackgroundTaskUtil.syncPublisher(myProject, VcsManagedFilesHolder.TOPIC).updatingModeChanged();
      ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();
      notifyExcludedSynchronizer(oldIgnored, newIgnored);
    }
    finally {
      BackgroundTaskUtil.syncPublisher(myProject, GitRefreshListener.TOPIC).progressStopped();
    }
  }

  private void applyRefreshResult(@NotNull RefreshResult result,
                                  @Nullable RecursiveFilePathSet dirtyScope,
                                  @NotNull Set<FilePath> oldIgnored) {
    if (dirtyScope != null) {
      myUntrackedFiles.removeIf(filePath -> dirtyScope.hasAncestor(filePath));
      myUntrackedFiles.addAll(result.untracked);

      myIgnoredFiles.clear();

      for (FilePath filePath : oldIgnored) {
        if (!dirtyScope.hasAncestor(filePath)) {
          myIgnoredFiles.add(filePath);
        }
      }
      for (FilePath filePath : result.ignored) {
        if (!myIgnoredFiles.hasAncestor(filePath)) { // prevent storing both parent and child directories
          myIgnoredFiles.add(filePath);
        }
      }
    }
    else {
      myUntrackedFiles.clear();
      myUntrackedFiles.addAll(result.untracked);
      myIgnoredFiles.clear();
      myIgnoredFiles.addAll(result.ignored);
    }
  }

  private void notifyExcludedSynchronizer(@NotNull Set<FilePath> oldIgnored, @NotNull List<FilePath> newIgnored) {
    List<FilePath> addedIgnored = new ArrayList<>();
    for (FilePath filePath : newIgnored) {
      if (!oldIgnored.contains(filePath)) {
        addedIgnored.add(filePath);
      }
    }
    if (!addedIgnored.isEmpty()) {
      myProject.getService(IgnoredToExcludedSynchronizer.class).ignoredUpdateFinished(addedIgnored);
    }
  }

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
        LOG.warn(String.format("Ignoring %s file under another root: %s; root: %s; mapped root: %s",
                               type, filePath.getPresentableUrl(), myRoot.getPresentableUrl(),
                               root != null ? root.getPresentableUrl() : "null"));
      }
    }
    if (removedFiles >= maxFilesToReport) {
      LOG.warn(String.format("Ignoring %s files under another root: %s files total", type, removedFiles));
    }
  }


  @NotNull
  private RefreshResult refreshFiles(@Nullable List<FilePath> dirty) {
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

  @NotNull
  private static FilePath getFilePath(@NotNull VirtualFile root, @NotNull StatusRecord status) {
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
      myQueue.waitForAllExecuted(10, TimeUnit.SECONDS);
    }
  }
}
