// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Internal
public final class KeyCollectionForwardIndexAccessor<Key, Value> extends AbstractForwardIndexAccessor<Key, Value, Collection<Key>> {
  public KeyCollectionForwardIndexAccessor(@NotNull DataExternalizer<Collection<Key>> externalizer) {
    super(externalizer);
  }

  public KeyCollectionForwardIndexAccessor(@NotNull IndexExtension<Key, Value, ?> extension) {
    this(new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName()));
  }

  @Override
  protected InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable Collection<Key> keys) {
    return new KeyCollectionInputDataDiffBuilder<>(inputId, keys != null ? keys : Collections.emptySet());
  }

  @Override
  public @Nullable Collection<Key> convertToDataType(@NotNull InputData<Key, Value> data) {
    Set<Key> keys = data.getKeyValues().keySet();
    return keys.isEmpty() ? null : keys;
  }

  @Override
  protected int getBufferInitialSize(@NotNull Collection<Key> keys) {
    return 4 * keys.size();
  }

  // Marks all keys as removed and then all new key-values as added. Does not try to find keys that haven't changed.
  private static final class KeyCollectionInputDataDiffBuilder<Key, Value> extends DirectInputDataDiffBuilder<Key, Value> {
    private final @NotNull Collection<Key> myKeys;

    KeyCollectionInputDataDiffBuilder(int inputId, @NotNull Collection<Key> keys) {
      super(inputId);
      myKeys = keys;
    }

    @Override
    public boolean differentiate(@NotNull Map<Key, Value> newData,
                                 @NotNull UpdatedEntryProcessor<? super Key, ? super Value> changesProcessor) throws StorageException {
      for (Key key : myKeys) {
        changesProcessor.removed(key, myInputId);
      }
      boolean anyAdded = EmptyInputDataDiffBuilder.processAllKeyValuesAsAdded(myInputId, newData, changesProcessor);
      boolean anyRemoved = !myKeys.isEmpty();
      return anyAdded || anyRemoved;
    }

    @Override
    public @NotNull Collection<Key> getKeys() {
      return myKeys;
    }
  }
}
