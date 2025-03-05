// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.diagnostics;

import com.intellij.util.indexing.impl.InvertedIndexValueIterator;
import com.intellij.util.indexing.impl.ValueContainerInputRemapping;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * ValueContainer implementation with additional self-consistency checks AND synchronized (well, kind of).
 * Not for production use: to be used during test runs
 */
@ApiStatus.Internal
public class SynchronizedValueContainerImplWithChecks<V> extends ValueContainerImplWithChecks<V> {

  public SynchronizedValueContainerImplWithChecks() {
  }

  @Override
  protected synchronized @NotNull String getDebugMessage() {
    return super.getDebugMessage();
  }

  @Override
  public synchronized void addValue(int inputId, V value) {
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
  protected synchronized void removeValue(int inputId, V value) {
    super.removeValue(inputId, value);
  }

  @Override
  public synchronized void readFrom(@NotNull DataInputStream stream,
                                    @NotNull DataExternalizer<? extends V> externalizer,
                                    @NotNull ValueContainerInputRemapping remapping) throws IOException {
    super.readFrom(stream, externalizer, remapping);
  }

  //TODO _iterator_ is not really synchronized
  @Override
  public synchronized @NotNull InvertedIndexValueIterator<V> getValueIterator() {
    return super.getValueIterator();
  }
}
