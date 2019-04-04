/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use {@link java.util.Base64} instead
 */
@Deprecated
public class Base64Converter {
  private static final char[] alphabet = {
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',   //  0 to  7
    'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',   //  8 to 15
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',   // 16 to 23
    'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',   // 24 to 31
    'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',   // 32 to 39
    'o', 'p', 'q', 'r', 's', 't', 'u', 'v',   // 40 to 47
    'w', 'x', 'y', 'z', '0', '1', '2', '3',   // 48 to 55
    '4', '5', '6', '7', '8', '9', '+', '/'};  // 56 to 63

  private static final byte[] decodeTable = {
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54, 55, 56, 57,
    58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
    10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1,
    -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
    40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1,
  };

  public static String encode(@NotNull String s) {
    return encode(s.getBytes());
  }

  public static String encode(@NotNull byte[] octetString) {
    int bits24;
    int bits6;

    char[] out
      = new char[((octetString.length - 1) / 3 + 1) * 4];

    int outIndex = 0;
    int i = 0;

    while ((i + 3) <= octetString.length) {
      // store the octets
      bits24 = (octetString[i++] & 0xFF) << 16;
      bits24 |= (octetString[i++] & 0xFF) << 8;
      bits24 |= (octetString[i++] & 0xFF);

      bits6 = (bits24 & 0x00FC0000) >> 18;
      out[outIndex++] = alphabet[bits6];
      bits6 = (bits24 & 0x0003F000) >> 12;
      out[outIndex++] = alphabet[bits6];
      bits6 = (bits24 & 0x00000FC0) >> 6;
      out[outIndex++] = alphabet[bits6];
      bits6 = (bits24 & 0x0000003F);
      out[outIndex++] = alphabet[bits6];
    }

    if (octetString.length - i == 2) {
      // store the octets
      bits24 = (octetString[i] & 0xFF) << 16;
      bits24 |= (octetString[i + 1] & 0xFF) << 8;

      bits6 = (bits24 & 0x00FC0000) >> 18;
      out[outIndex++] = alphabet[bits6];
      bits6 = (bits24 & 0x0003F000) >> 12;
      out[outIndex++] = alphabet[bits6];
      bits6 = (bits24 & 0x00000FC0) >> 6;
      out[outIndex++] = alphabet[bits6];

      // padding
      out[outIndex] = '=';
    }
    else if (octetString.length - i == 1) {
      // store the octets
      bits24 = (octetString[i] & 0xFF) << 16;

      bits6 = (bits24 & 0x00FC0000) >> 18;
      out[outIndex++] = alphabet[bits6];
      bits6 = (bits24 & 0x0003F000) >> 12;
      out[outIndex++] = alphabet[bits6];

      // padding
      out[outIndex++] = '=';
      out[outIndex] = '=';
    }

    return StringFactory.createShared(out);
  }

  public static String decode(@NotNull String s) {
    return new String(decode(s.getBytes()));
  }

  public static byte[] decode(@NotNull byte[] bytes) {
    int paddingCount = 0;
    int realLength = 0;

    for (int i = bytes.length - 1; i >= 0; i--) {
      if (bytes[i] > ' ') {
        realLength++;
      }

      if (bytes[i] == '=') {
        paddingCount++;
      }
    }

    if (realLength % 4 != 0) {
      throw new IllegalArgumentException("Incorrect length " + realLength + ". Must be a multiple of 4");
    }
    final byte[] out = new byte[realLength / 4 * 3 - paddingCount];
    final byte[] t = new byte[4];
    int outIndex = 0;
    int index = 0;
    t[0] = t[1] = t[2] = t[3] = '=';

    for (byte c : bytes) {
      if (c > ' ') {
        t[index++] = c;
      }

      if (index == 4) {
        outIndex += decode(out, outIndex, t[0], t[1], t[2], t[3]);
        index = 0;
        t[0] = t[1] = t[2] = t[3] = '=';
      }
    }

    if (index > 0) {
      decode(out, outIndex, t[0], t[1], t[2], t[3]);
    }
    return out;
  }

  private static int decode(byte[] output, int outIndex, byte a, byte b, byte c, byte d) {
    byte da = decodeTable[a];
    byte db = decodeTable[b];
    byte dc = decodeTable[c];
    byte dd = decodeTable[d];

    if ((da == -1) || (db == -1) || ((dc == -1) && (c != '=')) || ((dd == -1) && (d != '='))) {
      throw new IllegalArgumentException(
        "Invalid character [" + (a & 0xFF) + ", " + (b & 0xFF) + ", " + (c & 0xFF) + ", " + (d & 0xFF) + "]");
    }
    output[outIndex++] = (byte)((da << 2) | db >>> 4);

    if (c == '=') {
      return 1;
    }
    output[outIndex++] = (byte)((db << 4) | dc >>> 2);

    if (d == '=') {
      return 2;
    }
    output[outIndex] = (byte)((dc << 6) | dd);
    return 3;
  }
}
