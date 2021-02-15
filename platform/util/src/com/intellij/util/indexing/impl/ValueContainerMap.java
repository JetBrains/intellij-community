// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Dmitry Avdeev
 */
class ValueContainerMap<Key, Value> extends PersistentMapImpl<Key, UpdatableValueContainer<Value>> {
  @NotNull private final DataExternalizer<Value> myValueExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;

  ValueContainerMap(@NotNull Path file,
                    @NotNull KeyDescriptor<Key> keyKeyDescriptor,
                    @NotNull DataExternalizer<Value> valueExternalizer,
                    boolean keyIsUniqueForIndexedFile,
                    @NotNull ValueContainerInputRemapping inputRemapping,
                    boolean isReadonly,
                    boolean compactOnClose) throws IOException {
    super(PersistentMapBuilder
            .newBuilder(file, keyKeyDescriptor, new ValueContainerExternalizer<>(valueExternalizer, inputRemapping)).withReadonly(isReadonly).withCompactOnClose(compactOnClose));
    myValueExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
  }

  @Override
  protected void doPut(Key key, UpdatableValueContainer<Value> container) throws IOException {
    synchronized (getDataAccessLock()) {
      final ChangeTrackingValueContainer<Value> valueContainer = (ChangeTrackingValueContainer<Value>)container;

      // try to accumulate index value calculated for particular key to avoid fragmentation: usually keys are scattered across many files
      // note that keys unique for indexed file have their value calculated at once (e.g. key is file id, index calculates something for particular
      // file) and there is no benefit to accumulate values for particular key because only one value exists
      if (!valueContainer.needsCompacting() && !myKeyIsUniqueForIndexedFile) {
        appendData(key, new AppendablePersistentMap.ValueDataAppender() {
          @Override
          public void append(@NotNull final DataOutput out) throws IOException {
            valueContainer.saveTo(out, myValueExternalizer);
          }
        });
      }
      else {
        // rewrite the value container for defragmentation
        super.doPut(key, valueContainer);
      }
    }
  }

  @NotNull
  public ChangeTrackingValueContainer<Value> createChangeTrackingValueContainer(final Key key) {
    return new ChangeTrackingValueContainer<>(new ChangeTrackingValueContainer.Initializer<Value>() {
      @Override
      public @NotNull Object getLock() {
        return ValueContainerMap.this.getDataAccessLock();
      }

      @NotNull
      @Override
      public ValueContainer<Value> compute() {
        ValueContainer<Value> value;
        try {
          value = ValueContainerMap.this.get(key);
          if (value == null) {
            value = new ValueContainerImpl<>();
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return value;
      }
    });
  }

  private static final class ValueContainerExternalizer<T> implements DataExternalizer<UpdatableValueContainer<T>> {
    @NotNull private final DataExternalizer<T> myValueExternalizer;
    @NotNull private final ValueContainerInputRemapping myInputRemapping;

    private ValueContainerExternalizer(@NotNull DataExternalizer<T> valueExternalizer, @NotNull ValueContainerInputRemapping inputRemapping) {
      myValueExternalizer = valueExternalizer;
      myInputRemapping = inputRemapping;
    }

    @Override
    public void save(@NotNull final DataOutput out, @NotNull final UpdatableValueContainer<T> container) throws IOException {
      container.saveTo(out, myValueExternalizer);
    }

    @NotNull
    @Override
    public UpdatableValueContainer<T> read(@NotNull final DataInput in) throws IOException {
      final ValueContainerImpl<T> valueContainer = new ValueContainerImpl<>();

      valueContainer.readFrom((DataInputStream)in, myValueExternalizer, myInputRemapping);
      return valueContainer;
    }
  }
}
