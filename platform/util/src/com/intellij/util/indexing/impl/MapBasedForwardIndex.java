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

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class MapBasedForwardIndex implements ForwardIndex {
  @NotNull
  private volatile PersistentHashMap<Integer, ByteArraySequence> myInputsIndex;
  @NotNull
  private final File myIndexFile;
  private final boolean myUseChunks;

  public MapBasedForwardIndex(@NotNull File indexFile, boolean useChunks) throws IOException {
    myIndexFile = indexFile;
    myUseChunks = useChunks;
    myInputsIndex = createMap();
  }

  @NotNull
  private PersistentHashMap<Integer, ByteArraySequence> createMap()
    throws IOException {
    PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(!myUseChunks);
    try {
      return new PersistentHashMap<>(myIndexFile, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE);
    }
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
    }
  }

  @Override
  @Nullable
  public ByteArraySequence getInputData(int inputId) throws IOException {
    return myInputsIndex.get(inputId);
  }

  @Override
  public void putInputData(int inputId, @Nullable ByteArraySequence data) throws IOException {
    if (data != null) {
      myInputsIndex.put(inputId, data);
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
    final File baseFile = myInputsIndex.getBaseFile();
    try {
      myInputsIndex.close();
    }
    catch (Throwable ignored) {
    }
    if (baseFile != null) {
      IOUtil.deleteAllFilesStartingWith(baseFile);
    }
    myInputsIndex = createMap();
  }
}
