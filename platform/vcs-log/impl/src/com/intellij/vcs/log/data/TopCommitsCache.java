// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.vcs.log.VcsCommitMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TopCommitsCache {
  private static final Logger LOG = Logger.getInstance(TopCommitsCache.class);
  private final @NotNull VcsLogStorage myStorage;
  private final @NotNull IntObjectMap<VcsCommitMetadata> myCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private volatile @NotNull List<VcsCommitMetadata> mySortedDetails = new ArrayList<>();

  public TopCommitsCache(@NotNull VcsLogStorage storage) {
    myStorage = storage;
  }

  private int getIndex(@NotNull VcsCommitMetadata metadata) {
    return myStorage.getCommitIndex(metadata.getId(), metadata.getRoot());
  }

  public synchronized void storeDetails(@NotNull List<? extends VcsCommitMetadata> sortedDetails) {
    List<VcsCommitMetadata> newDetails = ContainerUtil.filter(sortedDetails, metadata -> !myCache.containsValue(metadata));
    if (newDetails.isEmpty()) return;
    Iterator<VcsCommitMetadata> it = new MergingIterator(mySortedDetails, newDetails);

    List<VcsCommitMetadata> result = new ArrayList<>();
    boolean isBroken = false;
    while (it.hasNext()) {
      VcsCommitMetadata detail = it.next();
      int index = getIndex(detail);
      if (index == VcsLogStorageImpl.NO_INDEX) {
        isBroken = true;
        continue; // means some error happened (and reported) earlier, nothing we can do here
      }
      if (result.size() < VcsLogData.getRecentCommitsCount() * 2) {
        result.add(detail);
        myCache.put(index, detail);
      }
      else {
        myCache.remove(index);
      }
    }
    LOG.assertTrue(result.size() == myCache.size() || isBroken,
                   result.size() + " details to store, yet " + myCache.size() + " indexes in cache.");
    mySortedDetails = result;
  }

  public @Nullable VcsCommitMetadata get(int index) {
    return myCache.get(index);
  }

  public synchronized void clear() {
    myCache.clear();
    mySortedDetails.clear();
  }

  private static final class MergingIterator implements Iterator<VcsCommitMetadata> {
    private final PeekingIterator<VcsCommitMetadata> myFirst;
    private final PeekingIterator<VcsCommitMetadata> mySecond;

    private MergingIterator(@NotNull List<VcsCommitMetadata> first, @NotNull List<VcsCommitMetadata> second) {
      myFirst = Iterators.peekingIterator(first.iterator());
      mySecond = Iterators.peekingIterator(second.iterator());
    }

    @Override
    public boolean hasNext() {
      return myFirst.hasNext() || mySecond.hasNext();
    }

    @Override
    public VcsCommitMetadata next() {
      if (!myFirst.hasNext()) return mySecond.next();
      if (!mySecond.hasNext()) return myFirst.next();
      VcsCommitMetadata data1 = myFirst.peek();
      VcsCommitMetadata data2 = mySecond.peek();
      // more recent commits (with bigger timestamp) should go first
      // if timestamp is the same, commit from the second list is chosen
      if (data1.getTimestamp() > data2.getTimestamp()) return myFirst.next();
      return mySecond.next();
    }
  }
}
