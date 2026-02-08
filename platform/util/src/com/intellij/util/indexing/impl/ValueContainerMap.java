// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.Processor;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.InlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMapBase;
import com.intellij.util.io.PersistentMapBuilder;
import com.intellij.util.io.PersistentMapImpl;
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
                                                .newBuilder(file, keyDescriptor,
                                                            new ValueContainerExternalizer<>(valueExternalizer, inputRemapping))
                                                .withReadonly(isReadonly)
                                                .withCompactOnClose(compactOnClose));
    myValueExternalizer = valueExternalizer;
    myKeyDescriptor = keyDescriptor;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
  }

  void merge(Key key,
             ChangeTrackingValueContainer<Value> valueContainer) throws IOException {
    // Try to accumulate index value calculated for particular key to avoid fragmentation: usually keys are scattered across many files.
    // Note: keys unique for indexed file have their value calculated at once (e.g. key is file id, index calculates something for
    // particular file) and there is no benefit to accumulate values for particular key because only one value exists.
    if (!valueContainer.needsCompacting() && !myKeyIsUniqueForIndexedFile) {
      myPersistentMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(final @NotNull DataOutput out) throws IOException {
          valueContainer.saveDiffTo(out, myValueExternalizer);
        }
      });
    }
    else {//needsCompacting || keyIsUniqueForIndexedFile
      //FIXME RC: here WAS a hack -- this branch processed a case (keyIsUniqueForIndexedFile=true && !needsCompacting)
      //          i.e. with diff and without mergedSnapshot. In this case the diff WAS written, but diff == snapshot
      //          because keyIsUnique.
      //          Currently CTValueContainer.saveTo() always store a merged snapshot -- which in this case creates a useless
      //          overhead, because it involves _reading_ the current content first -- just for it to be entirely overwritten
      //          by the new value.

      //rewrite the value container for defragmentation
      put(key, valueContainer);
    }
  }

  void put(Key key,
           UpdatableValueContainer<Value> valueContainer) throws IOException {
    myPersistentMap.put(key, valueContainer);
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
      UpdatableValueContainer<Value> value;
      try {
        value = myPersistentMap.get(key);
        if (value == null) {
          value = ValueContainerImpl.createNewValueContainer();
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
