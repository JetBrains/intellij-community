// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.xxh3;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntUnaryOperator;

/**
 * Characters are encoded using UTF-8. Not optimized for non-ASCII string.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public final class Xxh3 {
  private static final IntUnaryOperator H2LE = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                                               ? IntUnaryOperator.identity()
                                               : Integer::reverseBytes;

  public static long hash(byte[] input) {
    return Xxh3Impl.hash(input, ByteArrayAccess.INSTANCE, 0, input.length, 0);
  }

  public static long hash(byte[] input, int offset, int length) {
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

  public static int hash32(byte[] input) {
    // https://github.com/Cyan4973/xxHash/issues/453#issuecomment-696838445
    // grab the lower 32-bit
    return (int)hash(input);
  }

  /**
   * Characters are encoded using UTF-8.
   */
  public static long hash(String input) {
    return StringHash.longHash(input, 0, input.length(), 0);
  }

  // secret is shared - seeded hash only for universal hashing
  public static long seededHash(String input, long seed) {
    return StringHash.longHash(input, 0, input.length(), seed);
  }

  public static long seededHash(byte[] input, long seed) {
    return Xxh3Impl.hash(input, ByteArrayAccess.INSTANCE, 0, input.length, seed);
  }

  public static long hash(String input, int start, int length) {
    return StringHash.longHash(input, start, length, 0);
  }

  public static int hash32(String input) {
    return (int)hash(input);
  }

  public static long hashUnencodedChars(CharSequence input) {
    return Xxh3Impl.hash(input, CharSequenceAccess.INSTANCE, 0, input.length() * 2, 0);
  }

  public static int hashUnencodedChars32(CharSequence input) {
    return (int)hashUnencodedChars(input);
  }

  //<editor-fold desc="Ranges">

  public static long hashRange(String input, int start, int end) {
    return StringHash.longHash(input, start, end - start, 0);
  }

  //</editor-fold>

  public static long hashLongs(long[] input) {
    return Xxh3Impl.hash(input, LongArrayAccessForLongs.INSTANCE, 0, input.length * Long.BYTES, 0);
  }

  public static long hashInt(int input, final long seed) {
    input = H2LE.applyAsInt(input);
    long s = seed ^ Long.reverseBytes(seed & 0xFFFFFFFFL);
    // see https://github.com/OpenHFT/Zero-Allocation-Hashing/blob/fb8f9d40b9a2e10e83c74884d8945e0051164ed2/src/main/java/net/openhft/hashing/XXH3.java#L710
    // about this magic number - unsafeLE.i64(XXH3.XXH3_kSecret, 8+BYTE_BASE) ^ unsafeLE.i64(XXH3.XXH3_kSecret, 16+BYTE_BASE)
    long bitFlip = -4090762196417718878L - s;
    long keyed = ((input & 0xFFFFFFFFL) + (((long)input) << 32)) ^ bitFlip;
    return Xxh3Impl.rrmxmx(keyed, 4);
  }

  private static final class ByteBufferAccess extends Access<ByteBuffer> {
    private static final VarHandle LONG_HANDLE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    static final @NotNull Access<ByteBuffer> INSTANCE = new ByteBufferAccess();

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

  // special implementation for hashing long array - it is guaranteed that only i64 will be called (as input is aligned)
  private static final class LongArrayAccessForLongs extends Access<long[]> {
    static final LongArrayAccessForLongs INSTANCE = new LongArrayAccessForLongs();

    private LongArrayAccessForLongs() { }

    @Override
    public long i64(long[] input, int offset) {
      return input[offset >> 3];
    }

    @Override
    public int i32(long[] input, int offset) {
      long v = input[offset >> 3];
      return (offset & 7) == 0 ? (int)(v >> 32) : (int)v;
    }

    @Override
    protected int i8(long[] input, int offset) {
      throw new UnsupportedOperationException();
    }
  }
}