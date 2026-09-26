// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class EmptyForwardIndex implements ForwardIndex, MeasurableIndexStore {
  public static final EmptyForwardIndex INSTANCE = new EmptyForwardIndex();

  private EmptyForwardIndex() {
  }

  @Override
  public @Nullable ByteArraySequence get(@NotNull Integer key) {
    return null;
  }

  @Override
  public void put(@NotNull Integer key, @Nullable ByteArraySequence value) { }

  @Override
  public void clear() { }

  @Override
  public void close() { }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public void force() { }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public int keysCountApproximately() {
    return 0;
  }
}
