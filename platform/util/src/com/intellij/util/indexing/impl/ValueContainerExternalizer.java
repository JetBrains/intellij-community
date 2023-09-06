// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

@ApiStatus.Internal
@VisibleForTesting
public final class ValueContainerExternalizer<T> implements DataExternalizer<UpdatableValueContainer<T>> {
  private final @NotNull DataExternalizer<T> myValueExternalizer;
  private final @NotNull ValueContainerInputRemapping myInputRemapping;

  public ValueContainerExternalizer(@NotNull DataExternalizer<T> valueExternalizer, @NotNull ValueContainerInputRemapping inputRemapping) {
    myValueExternalizer = valueExternalizer;
    myInputRemapping = inputRemapping;
  }

  @Override
  public void save(final @NotNull DataOutput out, final @NotNull UpdatableValueContainer<T> container) throws IOException {
    container.saveTo(out, myValueExternalizer);
  }

  @Override
  public @NotNull UpdatableValueContainer<T> read(final @NotNull DataInput in) throws IOException {
    final ValueContainerImpl<T> valueContainer = ValueContainerImpl.createNewValueContainer();
    valueContainer.readFrom((DataInputStream)in, myValueExternalizer, myInputRemapping);
    return valueContainer;
  }
}
