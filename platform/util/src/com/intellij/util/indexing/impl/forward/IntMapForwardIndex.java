// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class IntMapForwardIndex implements IntForwardIndex {
  @NotNull
  private final Path myStorageFile;
  private final boolean myHasChunks;
  @NotNull
  private volatile PersistentMap<Integer, Integer> myPersistentMap;

  public IntMapForwardIndex(@NotNull Path storageFile,
                            boolean hasChunks) throws IOException {
    myStorageFile = storageFile;
    myHasChunks = hasChunks;
    myPersistentMap = createMap(myStorageFile, myHasChunks);
  }

  @NotNull
  private static PersistentMap<Integer, Integer> createMap(@NotNull Path storageFile,
                                                           boolean hasChunks) throws IOException {
    return PersistentMapBuilder
      .newBuilder(storageFile, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE)
      .inlineValues()
      .hasChunks(hasChunks)
      .build();
  }

  @Override
  public int getInt(int key) throws IOException {
    return myPersistentMap.get(key);
  }

  @Override
  public void putInt(int key, int value) throws IOException {
    myPersistentMap.put(key, value);
  }

  @Override
  public void force() {
    myPersistentMap.force();
  }

  @Override
  public void clear() throws IOException {
    myPersistentMap.closeAndClean();
    myPersistentMap = createMap(myStorageFile, myHasChunks);
  }

  @Override
  public void close() throws IOException {
    myPersistentMap.close();
  }
}
