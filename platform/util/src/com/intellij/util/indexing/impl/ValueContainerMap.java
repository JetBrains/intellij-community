// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.Processor;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;

final class ValueContainerMap<Key, Value> {
  private final @NotNull PersistentMapBase<Key, UpdatableValueContainer<Value>> myPersistentMap;
  private final @NotNull KeyDescriptor<Key> myKeyDescriptor;
  private final @NotNull DataExternalizer<Value> myValueExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;

  ValueContainerMap(@NotNull PersistentMapBase<Key, UpdatableValueContainer<Value>> persistentMap,
                    @NotNull KeyDescriptor<Key> keyDescriptor,
                    @NotNull DataExternalizer<Value> valueExternalizer,
                    boolean keyIsUniqueForIndexedFile) {
    myPersistentMap = persistentMap;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
  }

  ValueContainerMap(@NotNull Path file,
                    @NotNull KeyDescriptor<Key> keyDescriptor,
                    @NotNull DataExternalizer<Value> valueExternalizer,
                    boolean keyIsUniqueForIndexedFile,
                    @NotNull ValueContainerInputRemapping inputRemapping,
                    boolean isReadonly,
                    boolean compactOnClose) throws IOException {
    myPersistentMap = new PersistentMapImpl<>(PersistentMapBuilder
            .newBuilder(file, keyDescriptor, new ValueContainerExternalizer<>(valueExternalizer, inputRemapping))
            .withReadonly(isReadonly)
            .withCompactOnClose(compactOnClose));
    myValueExternalizer = valueExternalizer;
    myKeyDescriptor = keyDescriptor;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
  }

  void merge(Key key, UpdatableValueContainer<Value> valueContainer) throws IOException {
    // try to accumulate index value calculated for particular key to avoid fragmentation: usually keys are scattered across many files
    // note that keys unique for indexed file have their value calculated at once (e.g. key is file id, index calculates something for particular
    // file) and there is no benefit to accumulate values for particular key because only one value exists
    if (!valueContainer.needsCompacting() && !myKeyIsUniqueForIndexedFile) {
      myPersistentMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull final DataOutput out) throws IOException {
          valueContainer.saveTo(out, myValueExternalizer);
        }
      });
    }
    else {
      // rewrite the value container for defragmentation
      myPersistentMap.put(key, valueContainer);
    }
  }

  void remove(Key key) throws IOException {
    myPersistentMap.remove(key);
  }

  boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return myKeyDescriptor instanceof InlineKeyDescriptor
           // process keys and check that they're already present in map because we don't have separated key storage we must check keys
           ? myPersistentMap.processExistingKeys(processor)
           // optimization: process all keys, some of them might be already deleted but we don't care. We just read key storage file here
           : myPersistentMap.processKeys(processor);
  }

  @NotNull
  ChangeTrackingValueContainer<Value> getModifiableValueContainer(final Key key) {
    return new ChangeTrackingValueContainer<>(() -> {
      ValueContainer<Value> value;
      try {
        value = myPersistentMap.get(key);
        if (value == null) {
          value = new ValueContainerImpl<>();
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return value;
    });
  }

  void force() {
    try {
      myPersistentMap.force();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void close() throws IOException {
    myPersistentMap.close();
  }

  void closeAndDelete() throws IOException {
    myPersistentMap.closeAndDelete();
  }

  void markDirty() throws IOException {
    myPersistentMap.markDirty();
  }

  boolean isClosed() {
    return myPersistentMap.isClosed();
  }

  boolean isDirty() {
    return myPersistentMap.isDirty();
  }

  @TestOnly
  PersistentMapBase<Key, UpdatableValueContainer<Value>> getStorageMap() {
    return myPersistentMap;
  }
}
