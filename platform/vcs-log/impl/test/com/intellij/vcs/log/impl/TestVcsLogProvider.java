/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;

public class TestVcsLogProvider implements VcsLogProvider {

  public static final VcsRefType BRANCH_TYPE = new VcsRefType() {
    @Override
    public boolean isBranch() {
      return true;
    }

    @NotNull
    @Override
    public Color getBackgroundColor() {
      return Color.white;
    }
  };
  private static final String SAMPLE_SUBJECT = "Sample subject";
  private static final VcsUser STUB_USER = new VcsUserImpl("John Smith", "John.Smith@mail.com");

  @NotNull private final VirtualFile myRoot;
  @NotNull private final List<TimedVcsCommit> myCommits;
  @NotNull private final Set<VcsRef> myRefs;
  @NotNull private final MockRefManager myRefManager;
  @NotNull private final ReducibleSemaphore myFullLogSemaphore;
  @NotNull private final ReducibleSemaphore myRefreshSemaphore;
  private int myReadFirstBlockCounter;

  private final Function<TimedVcsCommit, VcsCommitMetadata> myCommitToMetadataConvertor = new Function<TimedVcsCommit, VcsCommitMetadata>(){
    @Override
    public VcsCommitMetadata fun(TimedVcsCommit commit) {
      return new VcsCommitMetadataImpl(commit.getId(), commit.getParents(), commit.getTimestamp(), myRoot, SAMPLE_SUBJECT, STUB_USER,
                                       SAMPLE_SUBJECT, STUB_USER, commit.getTimestamp());
    }
  };

  public TestVcsLogProvider(@NotNull VirtualFile root) {
    myRoot = root;
    myCommits = ContainerUtil.newArrayList();
    myRefs = ContainerUtil.newHashSet();
    myRefManager = new MockRefManager();
    myFullLogSemaphore = new ReducibleSemaphore();
    myRefreshSemaphore = new ReducibleSemaphore();
  }

  @NotNull
  @Override
  public List<? extends VcsCommitMetadata> readFirstBlock(@NotNull final VirtualFile root, @NotNull Requirements requirements)
    throws VcsException {
    if (requirements instanceof VcsLogProviderRequirementsEx && ((VcsLogProviderRequirementsEx)requirements).isRefresh()) {
      try {
        myRefreshSemaphore.acquire();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    myReadFirstBlockCounter++;
    assertRoot(root);
    return ContainerUtil.map(myCommits.subList(0, requirements.getCommitCount()), myCommitToMetadataConvertor);
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<VcsUser> userRegistry) throws VcsException {
    try {
      myFullLogSemaphore.acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    assertRoot(root);
    return myCommits;
  }

  private void assertRoot(@NotNull VirtualFile root) {
    assertEquals("Requested data for unknown root", myRoot, root);
  }

  @NotNull
  @Override
  public List<? extends VcsShortCommitDetails> readShortDetails(@NotNull VirtualFile root, @NotNull List<String> hashes)
    throws VcsException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<? extends VcsFullCommitDetails> readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<VcsRef> readAllRefs(@NotNull VirtualFile root) throws VcsException {
    return myRefs;
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VcsLogRefManager getReferenceManager() {
    return myRefManager;
  }

  @Override
  public void subscribeToRootRefreshEvents(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogRefresher refresher) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull VirtualFile root,
                                                       @NotNull VcsLogFilterCollection filterCollection,
                                                       int maxCount) throws VcsException {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public VcsUser getCurrentUser(@NotNull VirtualFile root) throws VcsException {
    return STUB_USER;
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException {
    throw new UnsupportedOperationException();
  }

  public void appendHistory(@NotNull List<TimedVcsCommit> commits) {
    myCommits.addAll(0, commits);
  }

  public void addRef(@NotNull VcsRef ref) {
    myRefs.add(ref);
  }

  public void blockRefresh() {
    myRefreshSemaphore.block();
  }

  public void unblockRefresh() {
    myRefreshSemaphore.unblock();
  }

  public void blockFullLog() throws InterruptedException {
    myFullLogSemaphore.block();
  }

  public void unblockFullLog() {
    myFullLogSemaphore.unblock();
  }

  public void resetReadFirstBlockCounter() {
    myReadFirstBlockCounter = 0;
  }

  public int getReadFirstBlockCounter() {
    return myReadFirstBlockCounter;
  }

  private static class MockRefManager implements VcsLogRefManager {
    @NotNull
    @Override
    public List<VcsRef> sort(Collection<VcsRef> refs) {
      return ContainerUtil.newArrayList(refs);
    }

    @NotNull
    @Override
    public List<RefGroup> group(Collection<VcsRef> refs) {
      return ContainerUtil.map(refs, new Function<VcsRef, RefGroup>() {
        @Override
        public RefGroup fun(VcsRef ref) {
          return new SingletonRefGroup(ref);
        }
      });
    }
  }

  private static class ReducibleSemaphore extends Semaphore {
    private boolean myBlocked;

    public ReducibleSemaphore() {
      super(1);
    }

    @Override
    public void acquire() throws InterruptedException {
      if (myBlocked) {
        super.acquire();
      }
    }

    public void block() {
      myBlocked = true;
      reducePermits(1);
    }

    public void unblock() {
      myBlocked = false;
      release();
    }

  }
}
