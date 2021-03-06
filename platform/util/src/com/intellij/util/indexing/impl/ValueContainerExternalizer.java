// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

final class ValueContainerExternalizer<T> implements DataExternalizer<UpdatableValueContainer<T>> {
  private final @NotNull DataExternalizer<T> myValueExternalizer;
  private final @NotNull ValueContainerInputRemapping myInputRemapping;

  ValueContainerExternalizer(@NotNull DataExternalizer<T> valueExternalizer, @NotNull ValueContainerInputRemapping inputRemapping) {
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
