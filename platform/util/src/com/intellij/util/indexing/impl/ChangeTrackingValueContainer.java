// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

/**
 * Container balances between keeping the changes as changes, and merging (applying) them.
 * I.e. if (inputId, value) tuple is removed, the container could either remove the tuple from mergedSnapshot
 * immediately, or keep the remove in invalidatedIds(+inputId), and apply it later. Same for add (inputId, value):
 * it could be either applied to mergedSnapshot immediately, or kept in 'added' container and applied later on,
 *
 * @author Eugene Zhuravlev
 */
@Internal
public class ChangeTrackingValueContainer<Value> extends UpdatableValueContainer<Value> {

  protected static class ThreadSafeIntSet {
    private final IntOpenHashSet delegate = new IntOpenHashSet(1);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private <T> T withWriteLock(Computable<T> block) {
      rwLock.writeLock().lock();
      try {
        return block.compute();
      }
      finally {
        rwLock.writeLock().unlock();
      }
    }

    private <T> T withReadLock(Computable<T> block) {
      rwLock.readLock().lock();
      try {
        return block.compute();
      }
      finally {
        rwLock.readLock().unlock();
      }
    }

    boolean add(int id) {
      return withWriteLock(() -> delegate.add(id));
    }

    void forEach(IntConsumer intConsumer) {
      withReadLock(() -> {
        delegate.forEach(intConsumer);
        return null;
      });
    }

    boolean isEmpty() {
      return withReadLock(() -> delegate.isEmpty());
    }

    int[] toIntArray() {
      return withReadLock(() -> delegate.toIntArray());
    }

    public void remove(int id) {
      withWriteLock(() -> {
        delegate.remove(id);
        return null;
      });
    }
  }

  // there is no volatile as we modify under write lock and read under read lock
  // TODO RC: myInvalidated can be updated under read lock (through FileBasedIndexImpl.ensureUpToDate),
  //          which technically means that we modify set under read lock.
  //          We have a lot of reports that IDE freezes in IntOpenHashSet.add, and it seems that we indeed never exit that method.
  //          Inside IntOpenHashSet.add there is a while loop. If it never exits, then we either have a problem with internal
  //          structure consistency, or we have a bug in IntOpenHashSet implementation. Former is more realistic, let's check
  //          if this is true by making myInvalidated thread-safe.
  protected ValueContainerImpl<Value> myAdded;
  protected ThreadSafeIntSet myInvalidated;


  //TODO RC: volatile field(s) here seems suspicious/ambiguous to me.
  //         This class in general is NOT thread-safe -- hence, it should be used either in single-threaded context,
  //         or it should be a responsibility of a caller to provide the thread-safety => it should be no need for
  //         volatile at all. But volatile is here.
  //         So it seems like even though the class is not thread-safe, but it is nevertheless used in multithreaded
  //         context, and the volatile is sort of 'poor-man way to make multithreading bugs less visible'. Which is
  //         obviously incorrect.
  /**
   * Cached snapshot of merged (stored + modified) data. should be accessed only read-only outside updatable container.
   * This object is always created by {@link #myInitializer}, hence if initializer is null -- it must also be null
   */
  private volatile ValueContainerImpl<Value> myMergedSnapshot;

  //TODO RC: the only reason to use UpdatableValueContainer instead of plain ValueContainer (unmodifiable) is to access
  //         .needsCompaction() method -- which is strictly speaking should be in a ValueContainer
  private final @NotNull Computable<? extends UpdatableValueContainer<Value>> myInitializer;

  public ChangeTrackingValueContainer(@NotNull Computable<? extends UpdatableValueContainer<Value>> initializer) {
    myInitializer = initializer;
  }

  @Override
  public void addValue(int inputId, Value value) {
    ValueContainerImpl<Value> mergedSnapshot = myMergedSnapshot;
    if (mergedSnapshot != null) {
      mergedSnapshot.addValue(inputId, value);
    }

    if (myAdded == null) {
      myAdded = ValueContainerImpl.createNewValueContainer();
    }
    myAdded.addValue(inputId, value);
  }

  @Override
  public boolean removeAssociatedValue(int inputId) {
    ValueContainerImpl<Value> mergedSnapshot = myMergedSnapshot;
    if (mergedSnapshot != null) {
      mergedSnapshot.removeAssociatedValue(inputId);
    }

    boolean wasRemovedFromAdded = removeFromAdded(inputId);
    //RC: It is teasing to short-circuit here if wasRemovedFromAdded=true -- seems like no need to add inputId to invalidatedIds?
    //    Wrong: inputId could be contained in the (not yet loaded) <mergedSnapshot> -- inputId still needs to be in invalidatedIds then.
    //    I.e. consider scenario:
    //    1) container X created: { merged=null, added=[], invalidated=[] }
    //       underlying container (to-be-mergedSnapshot, not yet loaded) = [..., (inputId, value), ... ]
    //    2) X.addValue(inputId, value) => X{ merged=null, added=[(inputId, value)], invalidated=[] }
    //    3) X.removeAssociatedValue(inputId) => X{ merged=null, added=[], invalidated=[] }
    //       I.e. the underlying container remains unchanged.
    //       But this is wrong: (inputId, value) must be removed from the underlying container

    boolean wasAddedToRemoved = addToRemoved(inputId);

    return wasRemovedFromAdded || wasAddedToRemoved;
  }

  private boolean addToRemoved(int inputId) {
    if (myInvalidated == null) {
      myInvalidated = new ThreadSafeIntSet();
    }
    return myInvalidated.add(inputId);
  }

  protected boolean removeFromAdded(int inputId) {
    return myAdded != null && myAdded.removeAssociatedValue(inputId);
  }

  @Override
  public int size() {
    return getMergedData().size();
  }

  @Override
  public @NotNull ValueContainer.ValueIterator<Value> getValueIterator() {
    return getMergedData().getValueIterator();
  }

  public void dropMergedData() {
    myMergedSnapshot = null;
  }

  private ValueContainerImpl<Value> getMergedData() {
    ValueContainerImpl<Value> mergedSnapshot = myMergedSnapshot;
    if (mergedSnapshot != null) {
      return mergedSnapshot;
    }

    UpdatableValueContainer<Value> fromDisk = myInitializer.compute();

    // it makes sense to check it again before cloning and modifications application
    mergedSnapshot = myMergedSnapshot;
    if (mergedSnapshot != null) {
      return mergedSnapshot;
    }

    ValueContainerImpl<Value> newMerged = fromDisk instanceof ValueContainerImpl
                                          ? ((ValueContainerImpl<Value>)fromDisk).clone()
                                          : ((ChangeTrackingValueContainer<Value>)fromDisk).getMergedData().clone();

    FileId2ValueMapping<Value> fileId2ValueMapping;
    if ((myAdded != null || myInvalidated != null) &&
        (newMerged.size() > ValueContainerImpl.NUMBER_OF_VALUES_THRESHOLD ||
         (myAdded != null && myAdded.size() > ValueContainerImpl.NUMBER_OF_VALUES_THRESHOLD))) {
      // Calculate file ids that have Value mapped to avoid O(NumberOfValuesInMerged) during removal
      fileId2ValueMapping = new FileId2ValueMapping<>(newMerged);
    }
    else {
      fileId2ValueMapping = null;
    }

    if (myInvalidated != null) {
      myInvalidated.forEach(inputId -> {
        if (fileId2ValueMapping != null) {
          fileId2ValueMapping.removeFileId(inputId);
        }
        else {
          newMerged.removeAssociatedValue(inputId);
        }
      });
    }

    if (myAdded != null) {
      myAdded.forEach((inputId, value) -> {
        // enforcing "one-value-per-file for particular key" invariant
        if (fileId2ValueMapping != null) {
          fileId2ValueMapping.removeFileId(inputId);
          fileId2ValueMapping.associateFileIdToValue(inputId, value);
        }
        else {
          newMerged.removeAssociatedValue(inputId);
          newMerged.addValue(inputId, value);
        }

        return true;
      });
    }

    setNeedsCompacting(fromDisk.needsCompacting());

    myMergedSnapshot = newMerged;
    return newMerged;
  }

  public boolean isDirty() {
    return (myAdded != null && myAdded.size() > 0)
           || (myInvalidated != null && !myInvalidated.isEmpty())
           || needsCompacting();
  }

  public boolean containsOnlyInvalidatedChange() {
    return (myInvalidated != null && !myInvalidated.isEmpty())
           && (myAdded == null || myAdded.size() == 0);
  }

  public boolean containsCachedMergedData() {
    return myMergedSnapshot != null;
  }

  @Override
  public void saveTo(@NotNull DataOutput out,
                     @NotNull DataExternalizer<? super Value> externalizer) throws IOException {
    getMergedData().saveTo(out, externalizer);
  }

  public void saveDiffTo(@NotNull DataOutput out,
                         @NotNull DataExternalizer<? super Value> externalizer) throws IOException {
    ThreadSafeIntSet set = myInvalidated;
    if (set != null && !set.isEmpty()) {
      for (int inputId : set.toIntArray()) {
        DataInputOutputUtil.writeINT(out, -inputId); // mark inputId as invalid, to be processed on load in ValueContainerImpl.readFrom
      }
    }

    final UpdatableValueContainer<Value> toAppend = myAdded;
    if (toAppend != null && toAppend.size() > 0) {
      toAppend.saveTo(out, externalizer);
    }
  }
}
