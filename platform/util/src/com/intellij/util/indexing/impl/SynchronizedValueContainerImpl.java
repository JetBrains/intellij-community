// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * {@link ValueContainerImpl} with all methods synchronized.
 * This is not enough to make it really and properly thread-safe, it is a cheap attempt to _try_ to do that.
 */
@ApiStatus.Internal
public final class SynchronizedValueContainerImpl<Value> extends ValueContainerImpl<Value> {
  SynchronizedValueContainerImpl() {
  }

  @Override
  public synchronized void addValue(int inputId, Value value) {
    super.addValue(inputId, value);
  }

  @Override
  public synchronized int size() {
    return super.size();
  }

  @Override
  public synchronized String toString() {
    return super.toString();
  }

  @Override
  protected synchronized void removeValue(int inputId, Value value) {
    super.removeValue(inputId, value);
  }

  @Override
  public synchronized void readFrom(@NotNull DataInputStream stream,
                                    @NotNull DataExternalizer<? extends Value> externalizer,
                                    @NotNull ValueContainerInputRemapping remapping) throws IOException {
    super.readFrom(stream, externalizer, remapping);
  }

  //TODO not really synchronized
  @Override
  public synchronized @NotNull InvertedIndexValueIterator<Value> getValueIterator() {
    return super.getValueIterator();
  }

  @Override
  @NotNull
  protected synchronized String getDebugMessage() {
    return super.getDebugMessage();
  }
}
