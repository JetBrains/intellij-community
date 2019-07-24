// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.CollectionInputDataDiffBuilder;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.InputIndexDataExternalizer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

@ApiStatus.Experimental
public class KeyCollectionForwardIndexAccessor<Key, Value> extends AbstractForwardIndexAccessor<Key, Value, Collection<Key>> {
  public KeyCollectionForwardIndexAccessor(@NotNull DataExternalizer<Collection<Key>> externalizer) {
    super(externalizer);
  }

  public KeyCollectionForwardIndexAccessor(@NotNull IndexExtension<Key, Value, ?> extension) {
    this(extension.getKeyDescriptor(), extension.getName());
  }

  public KeyCollectionForwardIndexAccessor(@NotNull KeyDescriptor<Key> externalizer, @NotNull IndexId<Key, Value> indexId) {
    super(new InputIndexDataExternalizer<>(externalizer, indexId));
  }

  @Override
  protected InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable Collection<Key> keys) {
    return new CollectionInputDataDiffBuilder<>(inputId, keys);
  }

  @Nullable
  @Override
  public Collection<Key> convertToDataType(@NotNull InputData<Key, Value> data) {
    Set<Key> keys = data.getKeyValues().keySet();
    return keys.isEmpty() ? null : keys;
  }

  @Override
  protected int getBufferInitialSize(@NotNull Collection<Key> keys) {
    return 4 * keys.size();
  }
}
