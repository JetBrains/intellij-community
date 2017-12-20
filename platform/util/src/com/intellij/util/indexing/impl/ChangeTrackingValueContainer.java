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
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeTrackingValueContainer<Value> extends UpdatableValueContainer<Value>{
  // there is no volatile as we modify under write lock and read under read lock
  private ValueContainerImpl<Value> myAdded;
  private TIntHashSet myInvalidated;
  private volatile ValueContainerImpl<Value> myMerged;
  private final Initializer<Value> myInitializer;

  public interface Initializer<T> extends Computable<ValueContainer<T>> {
    Object getLock();
  }

  public ChangeTrackingValueContainer(Initializer<Value> initializer) {
    myInitializer = initializer;
  }

  @Override
  public void addValue(int inputId, Value value) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.addValue(inputId, value);
    }

    if (myAdded == null) myAdded = new ValueContainerImpl<Value>();
    myAdded.addValue(inputId, value);
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.removeAssociatedValue(inputId);
    }

    if (myAdded != null) myAdded.removeAssociatedValue(inputId);

    if (myInvalidated == null) myInvalidated = new TIntHashSet(1);
    myInvalidated.add(inputId);
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
    synchronized (myInitializer.getLock()) {
      merged = myMerged;
      if (merged != null) {
        return merged;
      }

      FileId2ValueMapping<Value> fileId2ValueMapping = null;
      final ValueContainer<Value> fromDisk = myInitializer.compute();
      final ValueContainerImpl<Value> newMerged;

      if (fromDisk instanceof ValueContainerImpl) {
        newMerged = ((ValueContainerImpl<Value>)fromDisk).clone();
      } else {
        newMerged = ((ChangeTrackingValueContainer<Value>)fromDisk).getMergedData().clone();
      }

      if ((myAdded != null || myInvalidated != null) &&
          (newMerged.size() > ValueContainerImpl.NUMBER_OF_VALUES_THRESHOLD ||
           (myAdded != null && myAdded.size() > ValueContainerImpl.NUMBER_OF_VALUES_THRESHOLD))) {
        // Calculate file ids that have Value mapped to avoid O(NumberOfValuesInMerged) during removal
        fileId2ValueMapping = new FileId2ValueMapping<Value>(newMerged);
      }
      final FileId2ValueMapping<Value> finalFileId2ValueMapping = fileId2ValueMapping;
      if (myInvalidated != null) {
        myInvalidated.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int inputId) {
            if (finalFileId2ValueMapping != null) finalFileId2ValueMapping.removeFileId(inputId);
            else newMerged.removeAssociatedValue(inputId);
            return true;
          }
        });
      }

      if (myAdded != null) {
        if (fileId2ValueMapping != null) {
          // there is no sense for value per file validation because we have fileId -> value mapping and we are enforcing it here
          fileId2ValueMapping.disableOneValuePerFileValidation();
        }

        myAdded.forEach(new ValueContainer.ContainerAction<Value>() {
          @Override
          public boolean perform(final int inputId, final Value value) {
            // enforcing "one-value-per-file for particular key" invariant
            if (finalFileId2ValueMapping != null) finalFileId2ValueMapping.removeFileId(inputId);
            else newMerged.removeAssociatedValue(inputId);

            newMerged.addValue(inputId, value);
            if (finalFileId2ValueMapping != null) finalFileId2ValueMapping.associateFileIdToValue(inputId, value);
            return true;
          }
        });
      }
      setNeedsCompacting(((UpdatableValueContainer)fromDisk).needsCompacting());

      myMerged = newMerged;
      return newMerged;
    }
  }

  public boolean isDirty() {
    return (myAdded != null && myAdded.size() > 0) ||
           (myInvalidated != null && !myInvalidated.isEmpty()) ||
           needsCompacting();
  }

  public @Nullable UpdatableValueContainer<Value> getAddedDelta() {
    return myAdded;
  }

  @Override
  public void saveTo(DataOutput out, DataExternalizer<Value> externalizer) throws IOException {
    if (needsCompacting()) {
      getMergedData().saveTo(out, externalizer);
    } else {
      final TIntHashSet set = myInvalidated;
      if (set != null && set.size() > 0) {
        for (int inputId : set.toArray()) {
          DataInputOutputUtil.writeINT(out, -inputId); // mark inputId as invalid, to be processed on load in ValueContainerImpl.readFrom
        }
      }

      final UpdatableValueContainer<Value> toAppend = getAddedDelta();
      if (toAppend != null && toAppend.size() > 0) {
        toAppend.saveTo(out, externalizer);
      }
    }
  }

}
