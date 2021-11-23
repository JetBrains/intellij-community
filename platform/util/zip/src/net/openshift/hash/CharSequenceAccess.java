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

import java.nio.ByteOrder;

final class CharSequenceAccessHolder {
  static final CharSequenceAccess INSTANCE = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                                             ? CharSequenceAccess.LittleEndianCharSequenceAccess.INSTANCE
                                             : CharSequenceAccess.BigEndianCharSequenceAccess.INSTANCE;
}

abstract class CharSequenceAccess extends Access<CharSequence> {
  private static int ix(long offset) {
    return (int)(offset >> 1);
  }

  protected static long getLong(CharSequence input, long offset,
                                int char0Off, int char1Off, int char2Off, int char3Off,
                                int char4Off, int delta) {
    final int base = ix(offset);
    if (0 == ((int)offset & 1)) {
      final long char0 = input.charAt(base + char0Off);
      final long char1 = input.charAt(base + char1Off);
      final long char2 = input.charAt(base + char2Off);
      final long char3 = input.charAt(base + char3Off);
      return char0 | (char1 << 16) | (char2 << 32) | (char3 << 48);
    }
    else {
      final long char0 = input.charAt(base + char0Off + delta) >>> 8;
      final long char1 = input.charAt(base + char1Off + delta);
      final long char2 = input.charAt(base + char2Off + delta);
      final long char3 = input.charAt(base + char3Off + delta);
      final long char4 = input.charAt(base + char4Off);
      return char0 | (char1 << 8) | (char2 << 24) | (char3 << 40) | (char4 << 56);
    }
  }

  protected static long getUnsignedInt(CharSequence input, int offset,
                                       int char0Off, int char1Off, int char2Off, int delta) {
    final int base = ix(offset);
    if (0 == (offset & 1)) {
      final long char0 = input.charAt(base + char0Off);
      final long char1 = input.charAt(base + char1Off);
      return char0 | (char1 << 16);
    }
    else {
      final long char0 = input.charAt(base + char0Off + delta) >>> 8;
      final long char1 = input.charAt(base + char1Off + delta);
      final long char2 = Primitives.unsignedByte(input.charAt(base + char2Off));
      return char0 | (char1 << 8) | (char2 << 24);
    }
  }

  protected static char getUnsignedShort(CharSequence input,
                                         long offset, int char1Off, int delta) {
    if (0 == ((int)offset & 1)) {
      return input.charAt(ix(offset));
    }
    else {
      final int base = ix(offset);
      final int char0 = input.charAt(base + delta) >>> 8;
      final int char1 = input.charAt(base + char1Off);
      return (char)(char0 | (char1 << 8));
    }
  }

  protected static int getUnsignedByte(CharSequence input, int offset, int shift) {
    return Primitives.unsignedByte(input.charAt(ix(offset)) >> shift);
  }

  private CharSequenceAccess() { }

  @Override
  public int i32(CharSequence input, int offset) {
    return (int)u32(input, offset);
  }

  @Override
  public int i8(CharSequence input, int offset) {
    return (byte)u8(input, offset);
  }

  static final class LittleEndianCharSequenceAccess extends CharSequenceAccess {
    static final CharSequenceAccess INSTANCE = new LittleEndianCharSequenceAccess();

    private LittleEndianCharSequenceAccess() { }

    @Override
    public long i64(CharSequence input, int offset) {
      return getLong(input, offset, 0, 1, 2, 3, 4, 0);
    }

    @Override
    public long u32(CharSequence input, int offset) {
      return getUnsignedInt(input, offset, 0, 1, 2, 0);
    }

    @Override
    public int u8(CharSequence input, int offset) {
      return getUnsignedByte(input, offset, (offset & 1) << 3);
    }
  }

  static final class BigEndianCharSequenceAccess extends CharSequenceAccess {
    static final CharSequenceAccess INSTANCE = new BigEndianCharSequenceAccess();

    private BigEndianCharSequenceAccess() { }

    @Override
    public long i64(CharSequence input, int offset) {
      return getLong(input, offset, 3, 2, 1, 0, 0, 1);
    }

    @Override
    public long u32(CharSequence input, int offset) {
      return getUnsignedInt(input, offset, 1, 0, 0, 1);
    }

    @Override
    public int u8(CharSequence input, int offset) {
      return getUnsignedByte(input, offset, ((offset & 1) ^ 1) << 3);
    }
  }
}
