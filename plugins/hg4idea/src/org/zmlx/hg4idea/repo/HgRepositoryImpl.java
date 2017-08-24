/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.AsyncFilesManagerListener;
import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.ChangesViewI;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgBranchesCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.HgLocalIgnoredHolder;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.*;

public class HgRepositoryImpl extends RepositoryImpl implements HgRepository {

  private static final Logger LOG = Logger.getInstance(HgRepositoryImpl.class);

  @NotNull private HgVcs myVcs;
  @NotNull private final HgRepositoryReader myReader;
  @NotNull private final VirtualFile myHgDir;
  @NotNull private volatile HgRepoInfo myInfo;
  @NotNull private Set<String> myOpenedBranches = Collections.emptySet();

  @NotNull private volatile HgConfig myConfig;
  private boolean myIsFresh = true;
  private final HgLocalIgnoredHolder myLocalIgnoredHolder;


  @SuppressWarnings("ConstantConditions")
  private HgRepositoryImpl(@NotNull VirtualFile rootDir, @NotNull HgVcs vcs,
                           @NotNull Disposable parentDisposable) {
    super(vcs.getProject(), rootDir, parentDisposable);
    myVcs = vcs;
    myHgDir = rootDir.findChild(HgUtil.DOT_HG);
    assert myHgDir != null : ".hg directory wasn't found under " + rootDir.getPresentableUrl();
    myReader = new HgRepositoryReader(vcs, VfsUtilCore.virtualToIoFile(myHgDir));
    myConfig = HgConfig.getInstance(getProject(), rootDir);
    myLocalIgnoredHolder = new HgLocalIgnoredHolder(this);
    Disposer.register(this, myLocalIgnoredHolder);
    myLocalIgnoredHolder.addUpdateStateListener(new MyIgnoredHolderAsyncListener(getProject()));
    update();
  }

  @NotNull
  public static HgRepository getInstance(@NotNull VirtualFile root, @NotNull Project project,
                                         @NotNull Disposable parentDisposable) {
    HgVcs vcs = HgVcs.getInstance(project);
    if (vcs == null) {
      throw new IllegalArgumentException("Vcs not found for project " + project);
    }
    HgRepositoryImpl repository = new HgRepositoryImpl(root, vcs, parentDisposable);
    repository.setupUpdater();
    return repository;
  }

  private void setupUpdater() {
    HgRepositoryUpdater updater = new HgRepositoryUpdater(this);
    Disposer.register(this, updater);
    myLocalIgnoredHolder.startRescan();
  }

  @NotNull
  @Override
  public VirtualFile getHgDir() {
    return myHgDir;
  }

  @NotNull
  @Override
  public State getState() {
    return myInfo.getState();
  }

  @Nullable
  @Override
  /**
   * Return active bookmark name if exist or heavy branch name otherwise
   */
  public String getCurrentBranchName() {
    String branchOrBookMarkName = getCurrentBookmark();
    if (StringUtil.isEmptyOrSpaces(branchOrBookMarkName)) {
      branchOrBookMarkName = getCurrentBranch();
    }
    return branchOrBookMarkName;
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  @NotNull
  public String getCurrentBranch() {
    return myInfo.getCurrentBranch();
  }

  @Override
  @Nullable
  public String getCurrentRevision() {
    return myInfo.getCurrentRevision();
  }

  @Nullable
  public String getTipRevision() {
    return myInfo.getTipRevision();
  }

  @Override
  @NotNull
  public Map<String, LinkedHashSet<Hash>> getBranches() {
    return myInfo.getBranches();
  }

  @Override
  @NotNull
  public Set<String> getOpenedBranches() {
    return myOpenedBranches;
  }

  @NotNull
  @Override
  public Collection<HgNameWithHashInfo> getBookmarks() {
    return myInfo.getBookmarks();
  }

  @Nullable
  @Override
  public String getCurrentBookmark() {
    return myInfo.getCurrentBookmark();
  }

  @NotNull
  @Override
  public Collection<HgNameWithHashInfo> getTags() {
    return myInfo.getTags();
  }

  @NotNull
  @Override
  public Collection<HgNameWithHashInfo> getLocalTags() {
    return myInfo.getLocalTags();
  }

  @NotNull
  @Override
  public HgConfig getRepositoryConfig() {
    return myConfig;
  }

  @Override
  public boolean hasSubrepos() {
    return myInfo.hasSubrepos();
  }

  @NotNull
  public Collection<HgNameWithHashInfo> getSubrepos() {
    return myInfo.getSubrepos();
  }

  @NotNull
  @Override
  public List<HgNameWithHashInfo> getMQAppliedPatches() {
    return myInfo.getMQApplied();
  }

  @NotNull
  @Override
  public List<String> getAllPatchNames() {
    return myInfo.getMqPatchNames();
  }

  @NotNull
  @Override
  public List<String> getUnappliedPatchNames() {
    final List<String> appliedPatches = HgUtil.getNamesWithoutHashes(getMQAppliedPatches());
    return ContainerUtil.filter(getAllPatchNames(), s -> !appliedPatches.contains(s));
  }

  @Override
  public boolean isFresh() {
    return myIsFresh;
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

      HgUtil.executeOnPooledThread(() -> BackgroundTaskUtil.syncPublisher(project, HgVcs.STATUS_TOPIC).update(project, getRoot()), project);
    }
  }

  @NotNull
  @Override
  public String toLogString() {
    return "HgRepository " + getRoot() + " : " + myInfo;
  }

  @NotNull
  private HgRepoInfo readRepoInfo() {
    myIsFresh = myIsFresh && myReader.isFresh();
    //in GitRepositoryImpl there are temporary state object for reader fields storing! Todo Check;
    return
      new HgRepoInfo(myReader.readCurrentBranch(), myReader.readCurrentRevision(), myReader.readCurrentTipRevision(), myReader.readState(),
                     myReader.readBranches(),
                     myReader.readBookmarks(), myReader.readCurrentBookmark(), myReader.readTags(), myReader.readLocalTags(),
                     myReader.readSubrepos(), myReader.readMQAppliedPatches(), myReader.readMqPatchNames());
  }

  public void updateConfig() {
    myConfig = HgConfig.getInstance(getProject(), getRoot());
  }

  @Override
  public HgLocalIgnoredHolder getLocalIgnoredHolder() {
    return myLocalIgnoredHolder;
  }

  private static class MyIgnoredHolderAsyncListener implements AsyncFilesManagerListener {
    @NotNull private final ChangesViewI myChangesViewI;

    public MyIgnoredHolderAsyncListener(@NotNull Project project) {
      myChangesViewI = ChangesViewManager.getInstance(project);
    }

    @Override
    public void updateStarted() {
      myChangesViewI.scheduleRefresh();
    }

    @Override
    public void updateFinished() {
      myChangesViewI.scheduleRefresh();
    }
  }
}
