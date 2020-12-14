// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.util.List;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class TestVcsLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(TestVcsLogProvider.class);

  public static final VcsRefType BRANCH_TYPE = new VcsRefType() {
    @Override
    public boolean isBranch() {
      return true;
    }

    @NotNull
    @Override
    public Color getBackgroundColor() {
      return JBColor.WHITE;
    }
  };
  private static final String SAMPLE_SUBJECT = "Sample subject";
  public static final VcsUser DEFAULT_USER = new VcsUserImpl("John Smith", "John.Smith@mail.com");

  @NotNull private final List<TimedVcsCommit> myCommits;
  @NotNull private final Set<VcsRef> myRefs;
  @NotNull private final MockRefManager myRefManager;
  @NotNull private final ReducibleSemaphore myFullLogSemaphore;
  @NotNull private final ReducibleSemaphore myRefreshSemaphore;
  @NotNull private final AtomicInteger myReadFirstBlockCounter = new AtomicInteger();

  public TestVcsLogProvider() {
    myCommits = new ArrayList<>();
    myRefs = new HashSet<>();
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
    List<VcsCommitMetadata> metadatas = ContainerUtil.map(myCommits.subList(0, requirements.getCommitCount()),
                                                          commit -> createDefaultMetadataForCommit(root, commit));
    return new LogDataImpl(Collections.emptySet(), metadatas);
  }

  @NotNull
  private static VcsCommitMetadataImpl createDefaultMetadataForCommit(@NotNull VirtualFile root, TimedVcsCommit commit) {
    return new VcsCommitMetadataImpl(commit.getId(), commit.getParents(), commit.getTimestamp(), root, SAMPLE_SUBJECT,
                                     DEFAULT_USER, SAMPLE_SUBJECT, DEFAULT_USER, commit.getTimestamp());
  }

  @NotNull
  @Override
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<? super TimedVcsCommit> commitConsumer) {
    LOG.debug("readAllHashes");
    try {
      myFullLogSemaphore.acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    LOG.debug("readAllHashes passed the semaphore");
    for (TimedVcsCommit commit : myCommits) {
      commitConsumer.consume(commit);
    }
    return new LogDataImpl(myRefs, Collections.emptySet());
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void readMetadata(@NotNull VirtualFile root, @NotNull List<String> hashes, @NotNull Consumer<? super VcsCommitMetadata> consumer)
    throws VcsException {
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
  public Disposable subscribeToRootRefreshEvents(@NotNull Collection<? extends VirtualFile> roots, @NotNull VcsLogRefresher refresher) {
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
  public VcsUser getCurrentUser(@NotNull VirtualFile root) {
    return DEFAULT_USER;
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) {
    throw new UnsupportedOperationException();
  }

  public void appendHistory(@NotNull List<? extends TimedVcsCommit> commits) {
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

  private static class MockRefManager implements VcsLogRefManager {

    public static final Comparator<VcsRef> FAKE_COMPARATOR = (o1, o2) -> 0;

    @NotNull
    @Override
    public Comparator<VcsRef> getLabelsOrderComparator() {
      return FAKE_COMPARATOR;
    }

    @NotNull
    @Override
    public List<RefGroup> groupForBranchFilter(@NotNull Collection<? extends VcsRef> refs) {
      return ContainerUtil.map(refs, SingletonRefGroup::new);
    }

    @NotNull
    @Override
    public List<RefGroup> groupForTable(@NotNull Collection<? extends VcsRef> refs, boolean compact, boolean showTagNames) {
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

    @Override
    public boolean isFavorite(@NotNull VcsRef reference) {
      return false;
    }

    @Override
    public void setFavorite(@NotNull VcsRef reference, boolean favorite) {
    }

    @NotNull
    @Override
    public Comparator<VcsRef> getBranchLayoutComparator() {
      return FAKE_COMPARATOR;
    }
  }

  private static class ReducibleSemaphore extends Semaphore {
    private volatile boolean myBlocked;

    ReducibleSemaphore() {
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
