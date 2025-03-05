// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.ignore.VcsIgnoredHolderUpdateListener;
import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgDisposable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgBranchesCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.HgLocalIgnoredHolder;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.*;

import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;

public final class HgRepositoryImpl extends RepositoryImpl implements HgRepository {

  private static final Logger LOG = Logger.getInstance(HgRepositoryImpl.class);

  private final @NotNull HgVcs myVcs;
  private final @NotNull HgRepositoryReader myReader;
  private final @NotNull VirtualFile myHgDir;
  private volatile @NotNull HgRepoInfo myInfo;
  private @NotNull Set<String> myOpenedBranches = Collections.emptySet();

  private volatile @NotNull HgConfig myConfig;
  private final HgLocalIgnoredHolder myLocalIgnoredHolder;

  private final CoroutineScope coroutineScope;

  @SuppressWarnings("ConstantConditions")
  private HgRepositoryImpl(@NotNull VirtualFile rootDir, @NotNull HgVcs vcs) {
    super(vcs.getProject(), rootDir);
    myVcs = vcs;

    coroutineScope = childScope(HgDisposable.getCoroutineScope(myVcs.getProject()), "HgRepositoryImpl",
                                EmptyCoroutineContext.INSTANCE, true);

    myHgDir = rootDir.findChild(HgUtil.DOT_HG);
    assert myHgDir != null : ".hg directory wasn't found under " + rootDir.getPresentableUrl();
    myReader = new HgRepositoryReader(vcs, VfsUtilCore.virtualToIoFile(myHgDir));
    myConfig = HgConfig.getInstance(getProject(), rootDir);
    myLocalIgnoredHolder = new HgLocalIgnoredHolder(this, HgUtil.getRepositoryManager(getProject()));

    myLocalIgnoredHolder.setupListeners(coroutineScope);
    Disposer.register(this, myLocalIgnoredHolder);
    myLocalIgnoredHolder.addUpdateStateListener(new MyIgnoredHolderAsyncListener(getProject()));
    update();
  }

  public static @NotNull HgRepository getInstance(@NotNull VirtualFile root, @NotNull Project project,
                                                  @NotNull Disposable parentDisposable) {
    ProgressManager.checkCanceled();
    HgVcs vcs = HgVcs.getInstance(project);
    if (vcs == null) {
      throw new IllegalArgumentException("Vcs not found for project " + project);
    }
    HgRepositoryImpl repository = new HgRepositoryImpl(root, vcs);
    repository.setupUpdater();

    ReadAction.run(() -> {
      if (!Disposer.tryRegister(parentDisposable, repository)) {
        Disposer.dispose(repository);
      }
    });
    return repository;
  }

  private void setupUpdater() {
    HgRepositoryUpdater updater = new HgRepositoryUpdater(this);
    Disposer.register(this, updater);
    myLocalIgnoredHolder.startRescan();
  }

  @Override
  public @NotNull VirtualFile getHgDir() {
    return myHgDir;
  }

  @Override
  public @NotNull State getState() {
    return myInfo.getState();
  }

  /**
   * Return active bookmark name if exist or heavy branch name otherwise
   */
  @Override
  public @Nullable String getCurrentBranchName() {
    String branchOrBookMarkName = getCurrentBookmark();
    if (StringUtil.isEmptyOrSpaces(branchOrBookMarkName)) {
      branchOrBookMarkName = getCurrentBranch();
    }
    return branchOrBookMarkName;
  }

  @Override
  public @NotNull AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public @NotNull String getCurrentBranch() {
    return myInfo.getCurrentBranch();
  }

  @Override
  public @Nullable String getCurrentRevision() {
    return myInfo.getCurrentRevision();
  }

  @Override
  public @Nullable String getTipRevision() {
    return myInfo.getTipRevision();
  }

  @Override
  public @NotNull Map<String, LinkedHashSet<Hash>> getBranches() {
    return myInfo.getBranches();
  }

  @Override
  public @NotNull Set<String> getOpenedBranches() {
    return myOpenedBranches;
  }

  @Override
  public @NotNull Collection<HgNameWithHashInfo> getBookmarks() {
    return myInfo.getBookmarks();
  }

  @Override
  public @Nullable String getCurrentBookmark() {
    return myInfo.getCurrentBookmark();
  }

  @Override
  public @NotNull Collection<HgNameWithHashInfo> getTags() {
    return myInfo.getTags();
  }

  @Override
  public @NotNull Collection<HgNameWithHashInfo> getLocalTags() {
    return myInfo.getLocalTags();
  }

  @Override
  public @NotNull HgConfig getRepositoryConfig() {
    return myConfig;
  }

  @Override
  public boolean hasSubrepos() {
    return myInfo.hasSubrepos();
  }

  @Override
  public @NotNull Collection<HgNameWithHashInfo> getSubrepos() {
    return myInfo.getSubrepos();
  }

  @Override
  public @NotNull List<HgNameWithHashInfo> getMQAppliedPatches() {
    return myInfo.getMQApplied();
  }

  @Override
  public @NotNull List<String> getAllPatchNames() {
    return myInfo.getMqPatchNames();
  }

  @Override
  public @NotNull List<String> getUnappliedPatchNames() {
    final List<String> appliedPatches = HgUtil.getNamesWithoutHashes(getMQAppliedPatches());
    return ContainerUtil.filter(getAllPatchNames(), s -> !appliedPatches.contains(s));
  }

  @Override
  public void update() {
    HgRepoInfo currentInfo = readRepoInfo();
    // update only if something changed!!!   if update every time - new log will be refreshed every time, too.
    // Then blinking and do not work properly;
    final Project project = getProject();
    if (!project.isDisposed() && !currentInfo.equals(myInfo)) {
      myInfo = currentInfo;
      HgCommandResult branchCommandResult = new HgBranchesCommand(project, getRoot()).collectBranches();
      if (branchCommandResult == null || branchCommandResult.getExitValue() != 0) {
        LOG.warn("Could not collect hg opened branches."); // hg executable is not valid
        myOpenedBranches = myInfo.getBranches().keySet();
      }
      else {
        myOpenedBranches = HgBranchesCommand.collectNames(branchCommandResult);
      }

      BackgroundTaskUtil.executeOnPooledThread(this, ()
        -> BackgroundTaskUtil.syncPublisher(project, HgVcs.STATUS_TOPIC).update(project, getRoot()));
    }
  }

  @Override
  public @NonNls @NotNull String toLogString() {
    return "HgRepository " + getRoot() + " : " + myInfo;
  }

  private @NotNull HgRepoInfo readRepoInfo() {
    //in GitRepositoryImpl there are temporary state object for reader fields storing! Todo Check;
    return
      new HgRepoInfo(myReader.readCurrentBranch(), myReader.readCurrentRevision(), myReader.readCurrentTipRevision(), myReader.readState(),
                     myReader.readBranches(),
                     myReader.readBookmarks(), myReader.readCurrentBookmark(), myReader.readTags(), myReader.readLocalTags(),
                     myReader.readSubrepos(), myReader.readMQAppliedPatches(), myReader.readMqPatchNames());
  }

  @Override
  public void updateConfig() {
    myConfig = HgConfig.getInstance(getProject(), getRoot());
  }

  @Override
  public @NotNull HgLocalIgnoredHolder getIgnoredFilesHolder() {
    return myLocalIgnoredHolder;
  }

  private static class MyIgnoredHolderAsyncListener implements VcsIgnoredHolderUpdateListener {
    private final @NotNull Project myProject;

    MyIgnoredHolderAsyncListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void updateStarted() {
      BackgroundTaskUtil.syncPublisher(myProject, VcsManagedFilesHolder.TOPIC).updatingModeChanged();
    }

    @Override
    public void updateFinished(@NotNull Collection<FilePath> ignoredPaths, boolean isFullRescan) {
      if(myProject.isDisposed()) return;

      BackgroundTaskUtil.syncPublisher(myProject, VcsManagedFilesHolder.TOPIC).updatingModeChanged();
      ChangeListManagerImpl.getInstanceImpl(myProject).notifyUnchangedFileStatusChanged();
    }
  }
}
