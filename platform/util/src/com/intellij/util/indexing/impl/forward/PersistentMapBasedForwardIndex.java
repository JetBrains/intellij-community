// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentMapBasedForwardIndex implements ForwardIndex {
  @NotNull
  private volatile PersistentMap<Integer, ByteArraySequence> myPersistentMap;
  @NotNull
  private final Path myMapFile;
  private final boolean myUseChunks;
  private final boolean myReadOnly;

  public PersistentMapBasedForwardIndex(@NotNull Path mapFile, boolean isReadOnly) throws IOException {
    this(mapFile, true, isReadOnly);
  }

  public PersistentMapBasedForwardIndex(@NotNull Path mapFile, boolean useChunks, boolean isReadOnly) throws IOException {
    myPersistentMap = createMap(mapFile, useChunks, isReadOnly);
    myMapFile = mapFile;
    myUseChunks = useChunks;
    myReadOnly = isReadOnly;
  }

  @Nullable
  @Override
  public ByteArraySequence get(@NotNull Integer key) throws IOException {
    return myPersistentMap.get(key);
  }

  @Override
  public void put(@NotNull Integer key, @Nullable ByteArraySequence value) throws IOException {
    if (value == null) {
      myPersistentMap.remove(key);
    }
    else {
      myPersistentMap.put(key, value);
    }
  }

  @Override
  public void force() {
    myPersistentMap.force();
  }

  @Override
  public void clear() throws IOException {
    myPersistentMap.closeAndClean();
    myPersistentMap = createMap(myMapFile, myUseChunks, myReadOnly);
  }

  @Override
  public void close() throws IOException {
    myPersistentMap.close();
  }

  public boolean containsMapping(int key) throws IOException {
    return myPersistentMap.containsMapping(key);
  }

  @NotNull
  private static PersistentMap<Integer, ByteArraySequence> createMap(@NotNull Path file, boolean useChunks, boolean isReadOnly) throws IOException {
    return PersistentMapBuilder
      .newBuilder(file, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE)
      .hasChunks(useChunks)
      .withReadonly(isReadOnly)
      .build();
  }
}
