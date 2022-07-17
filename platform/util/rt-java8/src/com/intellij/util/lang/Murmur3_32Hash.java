/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.intellij.util.lang;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * See MurmurHash3_x86_32 in <a href="https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp">the C++ implementation</a>.
 * Copied from Guava because classloader module cannot depend on Guava.
 *
 * @author Austin Appleby
 * @author Dimitris Andreou
 * @author Kurt Alfred Kluever
 */
public final class Murmur3_32Hash {
  public static final Murmur3_32Hash MURMUR3_32 = new Murmur3_32Hash(0);

  private static final int CHUNK_SIZE = 4;

  private static final int C1 = 0xcc9e2d51;
  private static final int C2 = 0x1b873593;

  private final int seed;

  public Murmur3_32Hash(int seed) {
    this.seed = seed;
  }

  public int hashInt(int input) {
    int k1 = mixK1(input);
    int h1 = mixH1(seed, k1);

    return fMix(h1, 4);
  }

  public static int hashInt(int input, int seed) {
    int k1 = mixK1(input);
    int h1 = mixH1(seed, k1);

    return fMix(h1, 4);
  }

  public int hashLong(long input) {
    int low = (int)input;
    int high = (int)(input >>> 32);

    int k1 = mixK1(low);
    int h1 = mixH1(seed, k1);

    k1 = mixK1(high);
    h1 = mixH1(h1, k1);

    return fMix(h1, 8);
  }

  @SuppressWarnings("SpellCheckingInspection")
  public int hashUnencodedChars(CharSequence input) {
    int h1 = seed;

    // step through the CharSequence 2 chars at a time
    for (int i = 1; i < input.length(); i += 2) {
      int k1 = input.charAt(i - 1) | (input.charAt(i) << 16);
      k1 = mixK1(k1);
      h1 = mixH1(h1, k1);
    }

    // deal with any remaining characters
    if ((input.length() & 1) == 1) {
      int k1 = input.charAt(input.length() - 1);
      k1 = mixK1(k1);
      h1 ^= k1;
    }

    return fMix(h1, 2 * input.length());
  }

  public int hashString(CharSequence input) {
    return hashString(input, 0, input.length());
  }

  public int hashString(CharSequence input, int start, int end) {
    int h1 = seed;
    int i = start;
    int len = 0;

    // This loop optimizes for pure ASCII.
    while (i + 4 <= end) {
      char c0 = input.charAt(i);
      char c1 = input.charAt(i + 1);
      char c2 = input.charAt(i + 2);
      char c3 = input.charAt(i + 3);
      if (c0 < 0x80 && c1 < 0x80 && c2 < 0x80 && c3 < 0x80) {
        int k1 = c0 | (c1 << 8) | (c2 << 16) | (c3 << 24);
        k1 = mixK1(k1);
        h1 = mixH1(h1, k1);
        i += 4;
        len += 4;
      }
      else {
        break;
      }
    }

    long buffer = 0;
    int shift = 0;
    for (; i < end; i++) {
      char c = input.charAt(i);
      if (c < 0x80) {
        buffer |= (long)c << shift;
        shift += 8;
        len++;
      }
      else if (c < 0x800) {
        buffer |= charToTwoUtf8Bytes(c) << shift;
        shift += 16;
        len += 2;
      }
      else if (c < Character.MIN_SURROGATE || c > Character.MAX_SURROGATE) {
        buffer |= charToThreeUtf8Bytes(c) << shift;
        shift += 24;
        len += 3;
      }
      else {
        int codePoint = Character.codePointAt(input, i);
        if (codePoint == c) {
          // not a valid code point; let the JDK handle invalid Unicode
          byte[] bytes = input.toString().getBytes(StandardCharsets.UTF_8);
          return hashBytes(bytes, 0, bytes.length);
        }
        i++;
        buffer |= codePointToFourUtf8Bytes(codePoint) << shift;
        len += 4;
      }

      if (shift >= 32) {
        int k1 = mixK1((int)buffer);
        h1 = mixH1(h1, k1);
        buffer >>>= 32;
        shift -= 32;
      }
    }

    int k1 = mixK1((int)buffer);
    h1 ^= k1;
    return fMix(h1, len);
  }

  public int hashBytes(byte[] input, int off, int len) {
    checkPositionIndexes(off, off + len, input.length);
    int h1 = seed;
    int i;
    for (i = 0; i + CHUNK_SIZE <= len; i += CHUNK_SIZE) {
      int k1 = mixK1(getIntLittleEndian(input, off + i));
      h1 = mixH1(h1, k1);
    }

    int k1 = 0;
    for (int shift = 0; i < len; i++, shift += 8) {
      k1 ^= toInt(input[off + i]) << shift;
    }
    h1 ^= mixK1(k1);
    return fMix(h1, len);
  }

  private static int getIntLittleEndian(byte[] input, int offset) {
    return fromBytes(input[offset + 3], input[offset + 2], input[offset + 1], input[offset]);
  }

  private static int fromBytes(byte b1, byte b2, byte b3, byte b4) {
    return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
  }

  private static int mixK1(int k1) {
    k1 *= C1;
    k1 = Integer.rotateLeft(k1, 15);
    k1 *= C2;
    return k1;
  }

  private static int mixH1(int h1, int k1) {
    h1 ^= k1;
    h1 = Integer.rotateLeft(h1, 13);
    h1 = h1 * 5 + 0xe6546b64;
    return h1;
  }

  // Finalization mix - force all bits of a hash block to avalanche
  private static int fMix(int h1, int length) {
    h1 ^= length;
    h1 ^= h1 >>> 16;
    h1 *= 0x85ebca6b;
    h1 ^= h1 >>> 13;
    h1 *= 0xc2b2ae35;
    h1 ^= h1 >>> 16;
    return h1;
  }

  private static long codePointToFourUtf8Bytes(int codePoint) {
    return (((0xFL << 4) | (codePoint >>> 18)) & 0xFF)
           | ((0x80L | (0x3F & (codePoint >>> 12))) << 8)
           | ((0x80L | (0x3F & (codePoint >>> 6))) << 16)
           | ((0x80L | (0x3F & codePoint)) << 24);
  }

  private static long charToThreeUtf8Bytes(char c) {
    return (((0xF << 5) | (c >>> 12)) & 0xFF)
           | ((0x80 | (0x3F & (c >>> 6))) << 8)
           | ((0x80 | (0x3F & c)) << 16);
  }

  private static long charToTwoUtf8Bytes(char c) {
    return (((0xF << 6) | (c >>> 6)) & 0xFF) | ((0x80 | (0x3F & c)) << 8);
  }

  private static int toInt(byte value) {
    return value & 0xFF;
  }

  private static void checkPositionIndexes(int start, int end, int size) {
    if (start < 0 || end < start || end > size) {
      throw new IndexOutOfBoundsException(badPositionIndexes(start, end, size));
    }
  }

  private static String badPositionIndex(int index, int size, @Nullable String desc) {
    if (index < 0) {
      return desc + " (" + index + ") must not be negative";
    }
    else if (size < 0) {
      throw new IllegalArgumentException("negative size: " + size);
    }
    else { // index > size
      return desc + " (" + index + ") must not be greater than size (" + size + ")";
    }
  }

  private static String badPositionIndexes(int start, int end, int size) {
    if (start < 0 || start > size) {
      return badPositionIndex(start, size, "start index");
    }
    if (end < 0 || end > size) {
      return badPositionIndex(end, size, "end index");
    }
    // end < start
    return "end index (" + end + ") must not be less than start index (" + start + ")";
  }

  public static final class Murmur3_32Hasher {
    private int h1;
    private long buffer;
    private int shift;
    private int length;
    private boolean isDone;

    public Murmur3_32Hasher(int seed) {
      this.h1 = seed;
      this.length = 0;
      isDone = false;
    }

    private void update(int nBytes, long update) {
      // 1 <= nBytes <= 4
      buffer |= (update & 0xFFFFFFFFL) << shift;
      shift += nBytes * 8;
      length += nBytes;

      if (shift >= 32) {
        h1 = mixH1(h1, mixK1((int)buffer));
        buffer >>>= 32;
        shift -= 32;
      }
    }

    public void putByte(byte b) {
      update(1, b & 0xFF);
    }

    public void putBytes(byte[] bytes, int off, int len) {
      checkPositionIndexes(off, off + len, bytes.length);
      int i;
      for (i = 0; i + 4 <= len; i += 4) {
        update(4, getIntLittleEndian(bytes, off + i));
      }
      for (; i < len; i++) {
        putByte(bytes[off + i]);
      }
    }

    public void putBytes(ByteBuffer buffer) {
      ByteOrder bo = buffer.order();
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      while (buffer.remaining() >= 4) {
        putInt(buffer.getInt());
      }
      while (buffer.hasRemaining()) {
        putByte(buffer.get());
      }
      buffer.order(bo);
    }

    public void putInt(int i) {
      update(4, i);
    }

    public void putLong(long l) {
      update(4, (int)l);
      update(4, l >>> 32);
    }

    public void putChar(char c) {
      update(2, c);
    }

    public void putString(CharSequence input) {
      int utf16Length = input.length();
      int i = 0;

      // This loop optimizes for pure ASCII.
      while (i + 4 <= utf16Length) {
        char c0 = input.charAt(i);
        char c1 = input.charAt(i + 1);
        char c2 = input.charAt(i + 2);
        char c3 = input.charAt(i + 3);
        if (c0 < 0x80 && c1 < 0x80 && c2 < 0x80 && c3 < 0x80) {
          update(4, c0 | (c1 << 8) | (c2 << 16) | (c3 << 24));
          i += 4;
        }
        else {
          break;
        }
      }

      for (; i < utf16Length; i++) {
        char c = input.charAt(i);
        if (c < 0x80) {
          update(1, c);
        }
        else if (c < 0x800) {
          update(2, charToTwoUtf8Bytes(c));
        }
        else if (c < Character.MIN_SURROGATE || c > Character.MAX_SURROGATE) {
          update(3, charToThreeUtf8Bytes(c));
        }
        else {
          int codePoint = Character.codePointAt(input, i);
          if (codePoint == c) {
            // fall back to JDK getBytes instead of trying to handle invalid surrogates ourselves
            byte[] bytes = input.subSequence(i, utf16Length).toString().getBytes(StandardCharsets.UTF_8);
            putBytes(bytes, 0, bytes.length);
            return;
          }
          i++;
          update(4, codePointToFourUtf8Bytes(codePoint));
        }
      }
    }

    public int hash() {
      assert !isDone;
      isDone = true;
      h1 ^= mixK1((int)buffer);
      return fMix(h1, length);
    }
  }
}
