// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeTrackingValueContainer<Value> extends UpdatableValueContainer<Value>{
  // there is no volatile as we modify under write lock and read under read lock
  protected ValueContainerImpl<Value> myAdded;
  protected IntSet myInvalidated;

  // cached snapshot of merged (stored + modified) data. should be accessed only read-only outside updatable container
  private volatile ValueContainerImpl<Value> myMergedSnapshot;
  private final @Nullable Computable<? extends ValueContainer<Value>> myInitializer;
  
  public ChangeTrackingValueContainer(@Nullable Computable<? extends ValueContainer<Value>> initializer) {
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

    if (removeFromAdded(inputId)) {
      return true;
    }

    if (myInvalidated == null) myInvalidated = new IntOpenHashSet(1);
    myInvalidated.add(inputId);
    return true;
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

    ValueContainer<Value> fromDisk = myInitializer.compute();

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
    setNeedsCompacting(((UpdatableValueContainer<Value>)fromDisk).needsCompacting());

    myMergedSnapshot = newMerged;
    return newMerged;
  }

  public boolean isDirty() {
    return (myAdded != null && myAdded.size() > 0) ||
           (myInvalidated != null && !myInvalidated.isEmpty()) ||
           needsCompacting();
  }

  boolean containsOnlyInvalidatedChange() {
    return myInvalidated != null &&
           !myInvalidated.isEmpty() &&
           (myAdded == null || myAdded.size() == 0);
  }

  boolean containsCachedMergedData() {
    return myMergedSnapshot != null;
  }
  
  @Override
  public void saveTo(DataOutput out, DataExternalizer<? super Value> externalizer) throws IOException {
    if (needsCompacting()) {
      getMergedData().saveTo(out, externalizer);
    }
    else {
      IntSet set = myInvalidated;
      if (set != null && set.size() > 0) {
        for (int inputId : myInvalidated.toIntArray()) {
          DataInputOutputUtil.writeINT(out, -inputId); // mark inputId as invalid, to be processed on load in ValueContainerImpl.readFrom
        }
      }

      final UpdatableValueContainer<Value> toAppend = myAdded;
      if (toAppend != null && toAppend.size() > 0) {
        toAppend.saveTo(out, externalizer);
      }
    }
  }
}
