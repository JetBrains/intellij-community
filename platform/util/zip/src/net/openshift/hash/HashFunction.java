/*
 * Copyright 2014 Higher Frequency Trading http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openshift.hash;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Hash function producing {@code long}-valued result from byte sequences of any length and
 * a plenty of different sources which "feels like byte sequences". Except {@link
 * #hashBytes(byte[])} and {@link #hashBytes(ByteBuffer)} (with their "sliced" versions)
 * methods, which actually accept byte sequences, notion of byte
 * sequence is defined as follows:
 * <ul>
 *     <li>For methods accepting arrays of Java primitives, {@code String}s and
 *     {@code StringBuilder}s, byte sequence is how the input's bytes are actually lay in memory.
 *     </li>
 *     <li>For methods accepting single primitive values, byte sequence is how this primitive
 *     would be put into memory with {@link ByteOrder#nativeOrder() native} byte order, or
 *     equivalently, {@code hashXxx(primitive)} has always the same result as {@code
 *     hashXxx(new xxx[] {primitive})}, where "xxx" is any Java primitive type name.</li>
 *     <li>For {@link #hash(Object, Access, int, int)} method byte sequence abstraction
 *     is defined by the given {@link Access} strategy to the given object.</li>
 * </ul>
 *
 * <p>Hash function implementation could either produce equal results for equal input on platforms
 * with different {@link ByteOrder}, favoring one byte order in terms of performance, or different
 * results, but performing equally good. This choice should be explicitly documented for all
 * {@code LongHashFunction} implementations.
 *
 * <h2>Subclassing</h2>
 * To implement a specific hash function algorithm, this class should be subclassed. Only method
 * {@link #hash(Object, Access, int, int)}
 * should be implemented; other have default implementations which in the end delegate to
 * {@link #hash(Object, Access, int, int)} abstract method.
 *
 * <p>Notes about how exactly methods with default implementations are implemented in doc comments
 * are given for information and could be changed at any moment. However, it could hardly cause
 * any issues with subclassing, except probably little performance degradation. Methods documented
 * as "shortcuts" could either delegate to the referenced method or delegate directly to the method
 * to which the referenced method delegates.
 *
 * <p>{@code LongHashFunction} implementations shouldn't assume that {@code Access} strategies
 * do defensive checks, and access only bytes within the requested range.
 */
public abstract class HashFunction {
  /**
   * Returns the hash code for {@code len} continuous bytes of the given {@code input} object,
   * starting from the given offset. The abstraction of input as ordered byte sequence and
   * "offset within the input" is defined by the given {@code access} strategy.
   *
   * <p>This method doesn't promise to throw a {@code RuntimeException} if {@code
   * [off, off + len - 1]} subsequence exceeds the bounds of the bytes sequence, defined by {@code
   * access} strategy for the given {@code input}, so use this method with caution.
   *
   * @param input  the object to read bytes from
   * @param access access which defines the abstraction of the given input
   *               as ordered byte sequence
   * @param off    offset to the first byte of the subsequence to hash
   * @param len    length of the subsequence to hash
   * @param <T>    the type of the input
   * @return hash code for the specified bytes subsequence
   */
  protected abstract <T> long hash(T input, Access<T> access, int off, int len);

  /**
   * Shortcut for {@link #hashBytes(byte[], int, int) hashBytes(input, 0, input.length)}.
   */
  public long hashBytes(byte @NotNull [] input) {
    return hash(input, ByteArrayAccess.INSTANCE, 0, input.length);
  }

  /**
   * Returns the hash code for the specified subsequence of the given {@code byte} array.
   *
   * @param input the array to read bytes from
   * @param off   index of the first {@code byte} in the subsequence to hash
   * @param len   length of the subsequence to hash
   * @return hash code for the specified subsequence
   * @throws IndexOutOfBoundsException if {@code off < 0} or {@code off + len > input.length}
   *                                   or {@code len < 0}
   */
  public long hashBytes(byte @NotNull [] input, int off, int len) {
    checkBounds(off, len, input.length);
    return hash(input, ByteArrayAccess.INSTANCE, off, len);
  }

  /**
   * Shortcut for {@link #hashBytes(ByteBuffer, int, int)
   * hashBytes(input, input.position(), input.remaining())}.
   */
  public long hashBytes(@NotNull ByteBuffer input) {
    return hashByteBuffer(input, input.position(), input.remaining());
  }

  /**
   * Returns the hash code for the specified subsequence of the given {@code ByteBuffer}.
   *
   * <p>This method doesn't alter the state (mark, position, limit or order) of the given
   * {@code ByteBuffer}.
   *
   * @param input the buffer to read bytes from
   * @param off   index of the first {@code byte} in the subsequence to hash
   * @param len   length of the subsequence to hash
   * @return hash code for the specified subsequence
   * @throws IndexOutOfBoundsException if {@code off < 0} or {@code off + len > input.capacity()}
   *                                   or {@code len < 0}
   */
  public long hashBytes(@NotNull ByteBuffer input, int off, int len) {
    checkBounds(off, len, input.capacity());
    return hashByteBuffer(input, off, len);
  }

  public long hashLongs(long[] input) {
    return hash(input, LongArrayAccessForLongs.INSTANCE, 0, input.length * Long.BYTES);
  }

  private long hashByteBuffer(@NotNull ByteBuffer input, int off, int len) {
    if (input.hasArray()) {
      return hash(input.array(), ByteArrayAccess.INSTANCE, off, len);
    }
    else {
      return hash(input, ByteBufferAccess.INSTANCE, off, len);
    }
  }

  private static void checkBounds(int off, int len, int size) {
    if ((off | len | (off + len) | (size - (off + len))) < 0) {
      throw new IndexOutOfBoundsException();
    }
  }
}

// special implementation for hashing long array - it is guaranteed that only i64 will be called (as input is aligned)
final class LongArrayAccessForLongs extends Access<long[]> {
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

final class ByteBufferAccess extends Access<ByteBuffer> {
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