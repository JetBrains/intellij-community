// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CollectionDataExternalizer<K> implements DataExternalizer<Collection<K>> {
  private final DataExternalizer<K> myDataExternalizer;

  public CollectionDataExternalizer(@NotNull DataExternalizer<K> dataExternalizer) {
    myDataExternalizer = dataExternalizer;
  }

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<K> value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.size());
    for (K key : value) {
      myDataExternalizer.save(out, key);
    }
  }

  @Override
  public @NotNull Collection<K> read(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    if (size == 0) {
      return Collections.emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(myDataExternalizer.read(in));
    }
    List<K> list = new ArrayList<>(size);
    for (int idx = 0; idx < size; idx++) {
      list.add(myDataExternalizer.read(in));
    }
    return list;
  }
}
