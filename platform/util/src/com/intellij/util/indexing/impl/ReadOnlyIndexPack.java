// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.Computable;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ReadOnlyIndexPack<K, V, Input, Index extends InvertedIndex<K, V, Input>> implements InvertedIndex<K, V, Input> {
  @NotNull
  private final List<Index> myIndexes;

  public ReadOnlyIndexPack(@NotNull List<Index> indexes) {myIndexes = indexes;}

  @NotNull
  @Override
  public ValueContainer<V> getData(@NotNull K k) throws StorageException {
    List<ValueContainer<V>> result = new SmartList<>();
    for (InvertedIndex<K, V, Input> index : myIndexes) {
      ValueContainer<V> currentData = index.getData(k);
      if (currentData.size() != 0) {
        result.add(currentData);
      }
    }
    return result.isEmpty() ? new ValueContainerImpl<>() : new MergedValueContainer<>(result);
  }

  @NotNull
  @Override
  public Computable<Boolean> update(int inputId, @Nullable Input content) {
    throw new UnsupportedOperationException("index pack is read-only");
  }

  @Override
  public void flush() throws StorageException {
    List<StorageException> exceptions = new SmartList<>();
    for (InvertedIndex<K, V, Input> index : myIndexes) {
      try {
        index.flush();
      }
      catch (StorageException e) {
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
  }

  @Override
  public void clear() throws StorageException {
    throw new UnsupportedOperationException("index pack is read-only");
  }

  @Override
  public void dispose() {
    List<RuntimeException> exceptions = new SmartList<>();
    for (InvertedIndex<K, V, Input> index : myIndexes) {
      try {
        index.dispose();
      }
      catch (RuntimeException e) {
        exceptions.add(e);
      }
    }
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
  }
}
