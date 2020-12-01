// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class KeyCollectionForwardIndexAccessor<Key, Value> extends AbstractForwardIndexAccessor<Key, Value, Collection<Key>> {
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

  // Marks all keys as removed and then all new key-values as added. Does not try to find keys that haven't changed.
  private static final class KeyCollectionInputDataDiffBuilder<Key, Value> extends DirectInputDataDiffBuilder<Key, Value> {
    @NotNull
    private final Collection<Key> myKeys;

    KeyCollectionInputDataDiffBuilder(int inputId, @NotNull Collection<Key> keys) {
      super(inputId);
      myKeys = keys;
    }

    @Override
    public boolean differentiate(@NotNull Map<Key, Value> newData,
                                 @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                                 @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                                 @NotNull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException {
      for (Key key : myKeys) {
        removeProcessor.process(key, myInputId);
      }
      boolean anyAdded = EmptyInputDataDiffBuilder.processAllKeyValuesAsAdded(myInputId, newData, addProcessor);
      boolean anyRemoved = !myKeys.isEmpty();
      return anyAdded || anyRemoved;
    }

    @NotNull
    @Override
    public Collection<Key> getKeys() {
      return myKeys;
    }
  }
}
