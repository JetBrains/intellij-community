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

import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;
import java.util.Collections;


/**
 * @author Nadya Zabrodina
 */

public class HgRepositoryImpl extends RepositoryImpl implements HgRepository {

  @NotNull private final HgRepositoryReader myReader;
  @NotNull private final VirtualFile myHgDir;

  @NotNull private volatile String myCurrentBranch = DEFAULT_BRANCH;
  @Nullable private volatile String myCurrentBookmark = null;
  @NotNull private volatile Collection<String> myBranches = Collections.emptySet();
  @NotNull private volatile Collection<String> myBookmarks = Collections.emptySet();
  @NotNull private volatile HgConfig myConfig;
  private boolean myIsFresh = true;


  @SuppressWarnings("ConstantConditions")
  private HgRepositoryImpl(@NotNull VirtualFile rootDir, @NotNull Project project,
                             @NotNull Disposable parentDisposable) {
    super(project, rootDir, parentDisposable);
    myHgDir = rootDir.findChild(HgUtil.DOT_HG);
    assert myHgDir != null : ".hg directory wasn't found under " + rootDir.getPresentableUrl();
    myState = State.NORMAL;
    myCurrentRevision = null;
    myReader = new HgRepositoryReader(VfsUtilCore.virtualToIoFile(myHgDir));
    myConfig = HgConfig.getInstance(project, rootDir);
    update();
  }

  @NotNull
  public static HgRepository getInstance(@NotNull VirtualFile root, @NotNull Project project,
                                         @NotNull Disposable parentDisposable) {
    HgRepositoryImpl repository = new HgRepositoryImpl(root, project, parentDisposable);
    repository.setupUpdater();
    return repository;
  }

  private void setupUpdater() {
    HgRepositoryUpdater updater = new HgRepositoryUpdater(this);
    Disposer.register(this, updater);
  }

  @NotNull
  @Override
  public VirtualFile getHgDir() {
    return myHgDir;
  }

  @Override
  @NotNull
  public String getCurrentBranch() {
    return myCurrentBranch;
  }


  @Override
  @NotNull
  public Collection<String> getBranches() {
    return myBranches;
  }

  @NotNull
  @Override
  public Collection<String> getBookmarks() {
    return myBookmarks;
  }

  @Nullable
  @Override
  public String getCurrentBookmark() {
    return myCurrentBookmark;
  }

  @NotNull
  @Override
  public HgConfig getRepositoryConfig() {
    return myConfig;
  }

  @Override
  public boolean isFresh() {
    return myIsFresh;
  }

  @Override
  public void update() {
    readRepository();
    if (!Disposer.isDisposed(getProject())) {
      getProject().getMessageBus().syncPublisher(HgVcs.STATUS_TOPIC).update(getProject(), getRoot());
    }
  }

  @NotNull
  @Override
  public String toLogString() {
    return String.format("HgRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s, myRootDir=%s}",
                         myCurrentBranch, myCurrentRevision, myState, getRoot());
  }

  private void readRepository() {
    myIsFresh = myIsFresh && myReader.checkIsFresh(); //if repository not fresh  - it will be not fresh all time
    if (!isFresh()) {
      myState = myReader.readState();
      myCurrentRevision = myReader.readCurrentRevision();
      myCurrentBranch = myReader.readCurrentBranch();
      myBranches = myReader.readBranches();
      myBookmarks = myReader.readBookmarks();
      myCurrentBookmark = myReader.readCurrentBookmark();
    }
  }

  public void updateConfig(){
    myConfig = HgConfig.getInstance(getProject(),getRoot());
  }
}
