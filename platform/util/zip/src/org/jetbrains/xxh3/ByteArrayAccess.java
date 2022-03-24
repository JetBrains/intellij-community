// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.xxh3;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

final class ByteArrayAccess extends Access<byte[]> {
  private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  static final @NotNull ByteArrayAccess INSTANCE = new ByteArrayAccess();

  private ByteArrayAccess() { }

  @Override
  public long i64(byte[] input, int offset) {
    return (long)LONG_HANDLE.get(input, offset);
  }

  @Override
  public int i32(byte[] input, int offset) {
    return (int)INT_HANDLE.get(input, offset);
  }

  @Override
  public int i8(byte[] input, int offset) {
    return input[offset];
  }
}
