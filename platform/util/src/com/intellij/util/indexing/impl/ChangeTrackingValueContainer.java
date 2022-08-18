/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
  protected volatile ValueContainerImpl<Value> myMerged;
  private final @Nullable Computable<? extends ValueContainer<Value>> myInitializer;
  
  public ChangeTrackingValueContainer(@Nullable Computable<? extends ValueContainer<Value>> initializer) {
    myInitializer = initializer;
  }

  @Override
  public void addValue(int inputId, Value value) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.addValue(inputId, value);
    }

    if (myAdded == null) {
      myAdded = new ValueContainerImpl<>();
    }
    myAdded.addValue(inputId, value);
  }

  @Override
  public boolean removeAssociatedValue(int inputId) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.removeAssociatedValue(inputId);
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

  @NotNull
  @Override
  public ValueContainer.ValueIterator<Value> getValueIterator() {
    return getMergedData().getValueIterator();
  }

  public void dropMergedData() {
    myMerged = null;
  }

  // need 'synchronized' to ensure atomic initialization of merged data
  // because several threads that acquired read lock may simultaneously execute the method
  private ValueContainerImpl<Value> getMergedData() {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      return merged;
    }
    synchronized (this) {
      merged = myMerged;
      if (merged != null) {
        return merged;
      }

      ValueContainer<Value> fromDisk = myInitializer.compute();
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

      myMerged = newMerged;
      return newMerged;
    }
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
    return myMerged != null;
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
