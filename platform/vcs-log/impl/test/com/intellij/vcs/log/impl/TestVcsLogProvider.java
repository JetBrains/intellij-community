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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class TestVcsLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(TestVcsLogProvider.class);

  public static final VcsRefType BRANCH_TYPE = new VcsRefType() {
    @Override
    public boolean isBranch() {
      return true;
    }

    @NotNull
    @Override
    public ColorKey getBgColorKey() {
      return VcsLogColors.REFS_BRANCH;
    }
  };
  private static final String SAMPLE_SUBJECT = "Sample subject";
  public static final VcsUser DEFAULT_USER = new VcsUserImpl("John Smith", "John.Smith@mail.com");

  @NotNull private final VirtualFile myRoot;
  @NotNull private final List<TimedVcsCommit> myCommits;
  @NotNull private final Set<VcsRef> myRefs;
  @NotNull private final MockRefManager myRefManager;
  @NotNull private final ReducibleSemaphore myFullLogSemaphore;
  @NotNull private final ReducibleSemaphore myRefreshSemaphore;
  @NotNull private final AtomicInteger myReadFirstBlockCounter = new AtomicInteger();

  private final Function<TimedVcsCommit, VcsCommitMetadata> myCommitToMetadataConvertor =
    new Function<TimedVcsCommit, VcsCommitMetadata>() {
      @Override
      public VcsCommitMetadata fun(TimedVcsCommit commit) {
        return new VcsCommitMetadataImpl(commit.getId(), commit.getParents(), commit.getTimestamp(), myRoot, SAMPLE_SUBJECT, DEFAULT_USER,
                                         SAMPLE_SUBJECT, DEFAULT_USER, commit.getTimestamp());
      }
    };
  private Function<VcsLogFilterCollection, List<TimedVcsCommit>> myFilteredCommitsProvider;

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
  public DetailedLogData readFirstBlock(@NotNull final VirtualFile root, @NotNull Requirements requirements) {
    LOG.debug("readFirstBlock began");
    if (requirements instanceof VcsLogProviderRequirementsEx && ((VcsLogProviderRequirementsEx)requirements).isRefresh()) {
      try {
        myRefreshSemaphore.acquire();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    int readFirstBlockCounter = myReadFirstBlockCounter.incrementAndGet();
    LOG.debug("readFirstBlock passed the semaphore: " + readFirstBlockCounter);
    assertRoot(root);
    List<VcsCommitMetadata> metadatas = ContainerUtil.map(myCommits.subList(0, requirements.getCommitCount()),
                                                          myCommitToMetadataConvertor);
    return new LogDataImpl(Collections.emptySet(), metadatas);
  }

  @NotNull
  @Override
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<TimedVcsCommit> commitConsumer) {
    LOG.debug("readAllHashes");
    try {
      myFullLogSemaphore.acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    LOG.debug("readAllHashes passed the semaphore");
    assertRoot(root);
    for (TimedVcsCommit commit : myCommits) {
      commitConsumer.consume(commit);
    }
    return new LogDataImpl(myRefs, Collections.emptySet());
  }

  @Override
  public void readAllFullDetails(@NotNull VirtualFile root, @NotNull Consumer<VcsFullCommitDetails> commitConsumer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<VcsFullCommitDetails> commitConsumer,
                              boolean fast) {
    throw new UnsupportedOperationException();
  }

  private void assertRoot(@NotNull VirtualFile root) {
    assertEquals("Requested data for unknown root", myRoot, root);
  }

  @NotNull
  @Override
  public List<? extends VcsShortCommitDetails> readShortDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return MockAbstractVcs.getKey();
  }

  @NotNull
  @Override
  public VcsLogRefManager getReferenceManager() {
    return myRefManager;
  }

  @NotNull
  @Override
  public Disposable subscribeToRootRefreshEvents(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogRefresher refresher) {
    throw new UnsupportedOperationException();
  }

  public void setFilteredCommitsProvider(@NotNull Function<VcsLogFilterCollection, List<TimedVcsCommit>> provider) {
    myFilteredCommitsProvider = provider;
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull VirtualFile root,
                                                       @NotNull VcsLogFilterCollection filterCollection,
                                                       int maxCount) {
    if (myFilteredCommitsProvider == null) throw new UnsupportedOperationException();
    return myFilteredCommitsProvider.fun(filterCollection);
  }

  @Nullable
  @Override
  public VcsUser getCurrentUser(@NotNull VirtualFile root) {
    return DEFAULT_USER;
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) {
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

  public void blockFullLog() {
    myFullLogSemaphore.block();
  }

  public void unblockFullLog() {
    myFullLogSemaphore.unblock();
  }

  public void resetReadFirstBlockCounter() {
    myReadFirstBlockCounter.set(0);
  }

  public int getReadFirstBlockCounter() {
    return myReadFirstBlockCounter.get();
  }

  @Nullable
  @Override
  public <T> T getPropertyValue(VcsLogProperties.VcsLogProperty<T> property) {
    return null;
  }

  @Nullable
  @Override
  public String getCurrentBranch(@NotNull VirtualFile root) {
    return null;
  }

  @Nullable
  @Override
  public VcsLogDiffHandler getDiffHandler() {
    return null;
  }

  private static class MockRefManager implements VcsLogRefManager {

    public static final Comparator<VcsRef> FAKE_COMPARATOR = (o1, o2) -> 0;

    @NotNull
    @Override
    public Comparator<VcsRef> getLabelsOrderComparator() {
      return FAKE_COMPARATOR;
    }

    @NotNull
    @Override
    public List<RefGroup> groupForBranchFilter(@NotNull Collection<VcsRef> refs) {
      return ContainerUtil.map(refs, SingletonRefGroup::new);
    }

    @NotNull
    @Override
    public List<RefGroup> groupForTable(@NotNull Collection<VcsRef> refs, boolean compact, boolean showTagNames) {
      return groupForBranchFilter(refs);
    }

    @Override
    public void serialize(@NotNull DataOutput out, @NotNull VcsRefType type) {
    }

    @NotNull
    @Override
    public VcsRefType deserialize(@NotNull DataInput in) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Comparator<VcsRef> getBranchLayoutComparator() {
      return FAKE_COMPARATOR;
    }
  }

  private static class ReducibleSemaphore extends Semaphore {
    private volatile boolean myBlocked;

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
