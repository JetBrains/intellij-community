// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.MeasurableIndexStore;
import com.intellij.util.io.PersistentMap;
import com.intellij.util.io.PersistentMapBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public final class IntMapForwardIndex implements IntForwardIndex, MeasurableIndexStore {
  private final @NotNull Path myStorageFile;
  private final boolean myHasChunks;
  private volatile @NotNull PersistentMap<Integer, Integer> myPersistentMap;

  public IntMapForwardIndex(@NotNull Path storageFile,
                            boolean hasChunks) throws IOException {
    myStorageFile = storageFile;
    myHasChunks = hasChunks;
    myPersistentMap = createMap(myStorageFile, myHasChunks);
  }

  private static @NotNull PersistentMap<Integer, Integer> createMap(@NotNull Path storageFile,
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
  public void force() throws IOException {
    myPersistentMap.force();
  }

  @Override
  public boolean isDirty() {
    return myPersistentMap.isDirty();
  }

  @Override
  public int keysCountApproximately() {
    if (myPersistentMap instanceof MeasurableIndexStore) {
      return ((MeasurableIndexStore)myPersistentMap).keysCountApproximately();
    }
    return KEYS_COUNT_UNKNOWN;
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
