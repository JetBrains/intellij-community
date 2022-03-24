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
package org.jetbrains.xxh3;

import java.nio.ByteOrder;

/**
 * Strategy of reading bytes, defines the abstraction of {@code T} class instances as ordered byte
 * sequence. All {@code getXXX(input, offset)} should be consistent to each other in terms of
 * <i>ordered byte sequence</i> each {@code T} instance represents. For example, if some {@code
 * Access} implementation returns {@link ByteOrder#LITTLE_ENDIAN}
 * , the following expressions should always have the same value:
 * <ul>
 *     <li>{@code getLong(input, 0)}</li>
 *     <li>{@code getUnsignedInt(input, 0) | (getUnsignedInt(input, 4) << 32)}</li>
 *     <li><pre>{@code getUnsignedInt(input, 0) |
 *    ((long) getUnsignedShort(input, 4) << 32) |
 *    ((long) getUnsignedByte(input, 6) << 48) |
 *    ((long) getUnsignedByte(input, 7) << 56)}</pre></li>
 *   <li>And so on</li>
 * </ul>
 *
 * @param <T> the type of the object to access
 */
abstract class Access<T> {
  /**
   * Reads {@code [offset, offset + 7]} bytes of the byte sequence represented by the given
   * {@code input} as a single {@code long} value.
   *
   * @param input  the object to access
   * @param offset offset to the first byte to read within the byte sequence represented
   *               by the given object
   * @return eight bytes as a {@code long} value, in the expected byteOrder
   */
  public abstract long i64(T input, int offset);

  /**
   * Shortcut for {@code getInt(input, offset) & 0xFFFFFFFFL}. Could be implemented more
   * efficiently.
   *
   * @param input  the object to access
   * @param offset offset to the first byte to read within the byte sequence represented
   *               by the given object
   * @return four bytes as an unsigned int value, in the expected byteOrder
   */
  public long u32(T input, int offset) {
    return i32(input, offset) & 0xFFFFFFFFL;
  }

  /**
   * Reads {@code [offset, offset + 3]} bytes of the byte sequence represented by the given
   * {@code input} as a single {@code int} value.
   *
   * @param input  the object to access
   * @param offset offset to the first byte to read within the byte sequence represented
   *               by the given object
   * @return four bytes as an {@code int} value, in the expected byteOrder
   */
  public abstract int i32(T input, int offset);

  /**
   * Reads a single byte at the given {@code offset} in the byte sequence represented by the given
   * {@code input}, returned widened to {@code int}.
   *
   * @param input  the object to access
   * @param offset offset to the byte to read within the byte sequence represented
   *               by the given object
   * @return a byte by the given {@code offset}, widened to {@code int}
   */
  protected abstract int i8(T input, int offset);
}
