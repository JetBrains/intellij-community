// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IntForwardIndex extends ForwardIndex {

  int getInt(int key) throws IOException;

  void putInt(int  key, int value) throws IOException;

  @Nullable
  @Override
  default ByteArraySequence get(@NotNull Integer key) throws IOException {
    int intValue = getInt(key);
    return AbstractForwardIndexAccessor.serializeToByteSeq(intValue, EnumeratorIntegerDescriptor.INSTANCE, 4);
  }

  @Override
  default void put(@NotNull Integer key, @Nullable ByteArraySequence value) throws IOException {
    int valueAsInt = value == null ? 0 : AbstractForwardIndexAccessor.deserializeFromByteSeq(value, EnumeratorIntegerDescriptor.INSTANCE);
    putInt(key, valueAsInt);
  }
}
