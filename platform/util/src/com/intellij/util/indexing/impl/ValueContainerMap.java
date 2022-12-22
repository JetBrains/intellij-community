// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

final class ValueContainerMap<Key, Value> {
  private final @NotNull PersistentMapBase<Key, UpdatableValueContainer<Value>> myPersistentMap;
  private final @NotNull KeyDescriptor<Key> myKeyDescriptor;
  private final @NotNull DataExternalizer<Value> myValueExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;

  private final @Nullable ExecutorService mySerializationExecutor;
  private final @NotNull Map<Key, Future<?>> myPendingUpdates = ContainerUtil.newConcurrentMap();

  ValueContainerMap(@NotNull PersistentMapBase<Key, UpdatableValueContainer<Value>> persistentMap,
                    @NotNull KeyDescriptor<Key> keyDescriptor,
                    @NotNull DataExternalizer<Value> valueExternalizer,
                    @Nullable ExecutorService globalSerializationExecutor,
                    boolean keyIsUniqueForIndexedFile) {
    myPersistentMap = persistentMap;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    mySerializationExecutor = globalSerializationExecutor == null
                              ? null
                              : SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Value Container Map Serializer", globalSerializationExecutor);
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
    mySerializationExecutor = null;
  }

  private void flushPendingWrites() {
    if (mySerializationExecutor == null) return;
    for (Object key : myPendingUpdates.keySet().toArray()) {
      flushPendingWrite((Key)key);
    }
  }

  private void flushPendingWrite(Key key) {
    if (mySerializationExecutor == null) return;
    Future<?> future = myPendingUpdates.get(key);
    if (future != null) {
      if (!future.isDone()) {
        try {
          future.get();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      myPendingUpdates.remove(key, future);
    }
  }

  void merge(Key key, UpdatableValueContainer<Value> valueContainer) throws IOException {
    // try to accumulate index value calculated for particular key to avoid fragmentation: usually keys are scattered across many files
    // note that keys unique for indexed file have their value calculated at once (e.g. key is file id, index calculates something for particular
    // file) and there is no benefit to accumulate values for particular key because only one value exists
    if (!valueContainer.needsCompacting() && !myKeyIsUniqueForIndexedFile) {
      //performed under write-lock

      if (mySerializationExecutor != null) {
        Future<?>[] finalFuture = new Future[1];
        Future<?> future = mySerializationExecutor.submit(() -> {
          serializeAndMerge(key, valueContainer);
          Future<?> thisFuture = finalFuture[0];
          if (thisFuture != null) {
            myPendingUpdates.remove(key, thisFuture);
          }
        });
        finalFuture[0] = future;
        myPendingUpdates.put(key, future);
      }
      else {
        serializeAndMerge(key, valueContainer);
      }

    }
    else {
      flushPendingWrite(key);
      // rewrite the value container for defragmentation
      myPersistentMap.put(key, valueContainer);
    }
  }

  private void serializeAndMerge(@NotNull Key key, @NotNull UpdatableValueContainer<Value> valueContainer) {
    try {
      UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
      valueContainer.saveTo(new DataOutputStream(baos), myValueExternalizer);
      ByteArraySequence sequence = baos.toByteArraySequence();

      myPersistentMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull final DataOutput out) throws IOException {
          out.write(sequence.getInternalBuffer(), sequence.getOffset(), sequence.length());
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void remove(Key key) throws IOException {
    flushPendingWrite(key);
    myPersistentMap.remove(key);
  }

  boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    flushPendingWrites();
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
        flushPendingWrite(key);
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
      flushPendingWrites();
      myPersistentMap.force();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  void close() throws IOException {
    flushPendingWrites();
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
