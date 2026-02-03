// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.IndexId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

//TODO RC: why we need this class -- why not use CollectionDataExternalizer directly?
public final class InputIndexDataExternalizer<K> implements DataExternalizer<Collection<K>> {
  private final DataExternalizer<Collection<K>> myKeyCollectionExternalizer;
  /** Only for debug logging */
  private final IndexId<K, ?> myIndexId;

  public InputIndexDataExternalizer(KeyDescriptor<K> keyDescriptor, IndexId<K, ?> indexId) {
    myKeyCollectionExternalizer = new CollectionDataExternalizer<>(keyDescriptor);
    myIndexId = indexId;
  }

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<K> value) throws IOException {
    try {
      myKeyCollectionExternalizer.save(out, value);
    }
    catch (IllegalArgumentException e) {
      throw new IOException("Error saving data for index " + myIndexId, e);
    }
  }

  @Override
  public @NotNull Collection<K> read(@NotNull DataInput in) throws IOException {
    try {
      return myKeyCollectionExternalizer.read(in);
    }
    catch (IllegalArgumentException e) {
      throw new IOException("Error reading data for index " + myIndexId, e);
    }
  }
}
