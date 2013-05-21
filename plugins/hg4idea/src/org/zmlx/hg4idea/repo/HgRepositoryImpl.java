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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collections;
import java.util.List;


/**
 * @author Nadya Zabrodina
 */

public class HgRepositoryImpl implements HgRepository, Disposable {

  private static final Object STUB_OBJECT = new Object();

  private final Project myProject;
  private final VirtualFile myRootDir;
  private final HgRepositoryReader myReader;
  private final VirtualFile myHgDir;
  private final QueueProcessor<Object> myNotifier;

  @NotNull private volatile State myState =  State.NORMAL;
  @Nullable private volatile String myCurrentRevision = null;
  @NotNull private volatile String myCurrentBranch = DEFAULT_BRANCH;
  @NotNull private volatile List<String> myBranches = Collections.emptyList();


  /*
     * Get the HgRepository instance from the {@link HgRepositoryManager}.
     * If you need to have an instance of HgRepository for a repository outside the project, use
     * {@link #getLightInstance(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.project.Project, PlatformFacade, com.intellij.openapi.Disposable)}.

   */
  protected HgRepositoryImpl(@NotNull VirtualFile rootDir, @NotNull Project project,
                             @NotNull Disposable parentDisposable) {
    myRootDir = rootDir;
    myProject = project;
    Disposer.register(parentDisposable, this);

    myHgDir = myRootDir.findChild(HgUtil.DOT_HG);
    assert myHgDir != null : ".hg directory wasn't found under " + rootDir.getPresentableUrl();

    myReader = new HgRepositoryReader(VfsUtilCore.virtualToIoFile(myHgDir));

    MessageBus messageBus = project.getMessageBus();
    myNotifier = new QueueProcessor<Object>(new NotificationConsumer(myProject, messageBus, this), myProject.getDisposed());
    update();
  }


  public static HgRepository getFullInstance(@NotNull VirtualFile root, @NotNull Project project,
                                             @NotNull Disposable parentDisposable) {
    HgRepositoryImpl repository = new HgRepositoryImpl(root, project, parentDisposable);
    repository.setupUpdater();
    return repository;
  }

  private void setupUpdater() {
    HgRepositoryUpdater updater = new HgRepositoryUpdater(this);
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

  @NotNull
  @Override
  public VirtualFile getHgDir() {
    return myHgDir;
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
  public State getState() {
    return myState;
  }

  @Override
  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  @Override
  @NotNull
  public String getCurrentBranch() {
    return myCurrentBranch;
  }


  @Override
  @NotNull
  public List<String> getBranches() {
    return myBranches;
  }


  @Override
  public boolean isFresh() {
    return !myReader.headExist();
  }

  @Override
  public void update() {
    readRepository();
    notifyListeners();
  }

  private void readRepository() {
    if (!isFresh()) {
      myState = myReader.readState();
      myCurrentRevision = myReader.readCurrentRevision();
      myCurrentBranch = myReader.readCurrentBranch();
      myBranches = myReader.readBranches();
    }
  }

  protected void notifyListeners() {
    myNotifier.add(STUB_OBJECT);     // we don't have parameters for listeners
  }

  private static class NotificationConsumer implements Consumer<Object> {

    private final Project myProject;
    private final MessageBus myMessageBus;
    private final HgRepository myRepository;

    NotificationConsumer(Project project, MessageBus messageBus, HgRepository repository) {
      myProject = project;
      myMessageBus = messageBus;
      myRepository = repository;
    }

    @Override
    public void consume(Object o) {
      if (!Disposer.isDisposed(myProject)) {
        myMessageBus.syncPublisher(HgVcs.STATUS_TOPIC).update(myProject, myRepository.getRoot());
      }
    }
  }

  @Override
  public String toString() {
    return getPresentableUrl();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HgRepositoryImpl that = (HgRepositoryImpl)o;

    if (!myProject.equals(that.myProject)) return false;
    if (!myRootDir.equals(that.myRootDir)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + (myRootDir.hashCode());
    return result;
  }
}
