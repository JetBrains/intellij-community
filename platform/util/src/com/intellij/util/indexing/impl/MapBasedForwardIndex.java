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

import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.io.PersistentMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public abstract class MapBasedForwardIndex<Key, Value, MapValueType> extends AbstractForwardIndex<Key,Value> {
  @NotNull
  private volatile PersistentMap<Integer, MapValueType> myInputsIndex;

  protected MapBasedForwardIndex(IndexExtension<Key, Value, ?> indexExtension) throws IOException {
    super(indexExtension);
    myInputsIndex = createMap();
  }

  @NotNull
  public abstract PersistentMap<Integer, MapValueType> createMap() throws IOException;

  @NotNull
  @Override
  public InputDataDiffBuilder<Key, Value> getDiffBuilder(final int inputId) throws IOException {
    return getDiffBuilder(inputId, getInput(inputId));
  }

  protected abstract InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, MapValueType mapValueType) throws IOException;
  protected abstract MapValueType convertToMapValueType(int inputId, Map<Key, Value> map) throws IOException;

  public MapValueType getInput(int inputId) throws IOException {
    return myInputsIndex.get(inputId);
  }

  @Override
  public void putInputData(int inputId, @NotNull Map<Key, Value> data) throws IOException {
    if (!data.isEmpty()) {
      myInputsIndex.put(inputId, convertToMapValueType(inputId, data));
    }
    else {
      myInputsIndex.remove(inputId);
    }
  }

  @Override
  public void flush() {
    if (myInputsIndex.isDirty()) {
      myInputsIndex.force();
    }
  }

  @Override
  public void close() throws IOException {
    myInputsIndex.close();
  }

  @Override
  public void clear() throws IOException {
    try {
      myInputsIndex.close();
    }
    catch (Throwable ignored) {
    }
    myInputsIndex.clear();
    myInputsIndex = createMap();
  }
}
