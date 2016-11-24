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

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

@ApiStatus.Experimental
public abstract class UpdateData<Key, Value> {
  protected final Map<Key, Value> myNewData;
  protected final ThrowableComputable<ForwardIndex.InputKeyIterator<Key, Value>, IOException> myCurrentData;
  private final ID<Key, Value> myIndexId;
  private final ThrowableRunnable<IOException> myForwardIndexUpdate;

  protected UpdateData(@NotNull Map<Key, Value> newData,
                       @NotNull ThrowableComputable<ForwardIndex.InputKeyIterator<Key, Value>, IOException> currentData,
                       @NotNull ID<Key, Value> indexId,
                       @Nullable ThrowableRunnable<IOException> forwardIndexUpdate) {
    myNewData = newData;
    myCurrentData = currentData;
    myIndexId = indexId;
    myForwardIndexUpdate = forwardIndexUpdate;
  }

  public abstract void iterateKeys(final int inputId,
                                   final KeyValueUpdateProcessor<Key, Value> addProcessor,
                                   final KeyValueUpdateProcessor<Key, Value> updateProcessor,
                                   final RemovedKeyProcessor<Key> removeProcessor) throws StorageException;

  public Map<Key, Value> getNewData() {
    return myNewData;
  }

  public interface KeyValueUpdateProcessor<Key, Value> {
    void process(Key key, Value value, int inputId) throws StorageException;
  }

  public interface RemovedKeyProcessor<Key> {
    void process(Key key, int inputId) throws StorageException;
  }

  public ID<Key, Value> getIndexId() {
    return myIndexId;
  }

  public void updateForwardIndex() throws IOException {
    if (myForwardIndexUpdate != null) {
      myForwardIndexUpdate.run();
    }
  }

  @Override
  public String toString() {
    return myIndexId + "," + getClass().getName();
  }
}
