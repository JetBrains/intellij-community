// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Characters are encoded using UTF-8. Not optimized for non-ASCII string.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public final class Xxh3 {
  public static long hash(byte @NotNull [] input) {
    return Xxh3Impl.hash(input, ByteArrayAccess.INSTANCE, 0, input.length, 0);
  }

  public static long hash(@NotNull InputStream inputStream, int length) {
    return Xxh3Impl.hash(inputStream, new InputStreamAccess(length), 0, length, 0);
  }

  public static long hash(byte @NotNull [] input, int offset, int length) {
    return Xxh3Impl.hash(input, ByteArrayAccess.INSTANCE, offset, length, 0);
  }

  public static long hash(@NotNull ByteBuffer input) {
    return hash(input, input.position(), input.remaining());
  }

  public static long hash(@NotNull ByteBuffer input, int offset, int length) {
    if (input.hasArray()) {
      return Xxh3Impl.hash(input.array(), ByteArrayAccess.INSTANCE, input.arrayOffset() + offset, length, 0);
    }
    else {
      return Xxh3Impl.hash(input, ByteBufferAccess.INSTANCE, offset, length, 0);
    }
  }

  /**
   * Characters are encoded using UTF-8.
   */
  public static long hash(@NotNull String input) {
    byte[] data = input.getBytes(StandardCharsets.UTF_8);
    return Xxh3Impl.hash(data, ByteArrayAccess.INSTANCE, 0, data.length, 0);
  }

  private static final class ByteBufferAccess implements Access<ByteBuffer> {
    private static final VarHandle LONG_HANDLE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private static final @NotNull Access<ByteBuffer> INSTANCE = new ByteBufferAccess();

    private ByteBufferAccess() { }

    @Override
    public long i64(ByteBuffer input, int offset) {
      return (long)LONG_HANDLE.get(input, offset);
    }

    @Override
    public int i32(ByteBuffer input, int offset) {
      return (int)INT_HANDLE.get(input, offset);
    }

    @Override
    public int i8(ByteBuffer input, int offset) {
      return input.get(offset);
    }
  }
}