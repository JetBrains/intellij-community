// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class PersistentMapBasedForwardIndex implements ForwardIndex {
  private static final Logger LOG = Logger.getInstance(PersistentMapBasedForwardIndex.class);
  @NotNull
  private volatile PersistentHashMap<Integer, ByteArraySequence> myPersistentMap;
  @NotNull
  private final File myMapFile;

  public PersistentMapBasedForwardIndex(@NotNull File mapFile) throws IOException {
    myPersistentMap = createMap(mapFile);
    myMapFile = mapFile;
  }

  @NotNull
  protected PersistentHashMap<Integer, ByteArraySequence> createMap(File file) throws IOException {
    return new PersistentHashMap<>(file, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE);
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
    File baseFile = myPersistentMap.getBaseFile();
    try {
      myPersistentMap.close();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    PersistentHashMap.deleteFilesStartingWith(baseFile);
    myPersistentMap = createMap(myMapFile);
  }

  @Override
  public void close() throws IOException {
    myPersistentMap.close();
  }

  public boolean isBusyReading() {
    return myPersistentMap.isBusyReading();
  }

  public boolean containsMapping(int key) throws IOException {
    return myPersistentMap.containsMapping(key);
  }
}
