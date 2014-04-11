/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

public class Base64 {
  private Base64() {
  }

  public static String encode(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < bytes.length; i += 3) {
      builder.append(encodeBlock(bytes, i));
    }
    return builder.toString();
  }

  private static char[] encodeBlock(byte[] bytes, int offset) {
    int j = 0;
    int s = bytes.length - offset - 1;
    int l = s < 2 ? s : 2;
    for (int i = 0; i <= l; i++) {
      byte b = bytes[offset + i];
      int n = b >= 0 ? ((int)(b)) : b + 256;
      j += n << 8 * (2 - i);
    }
    char[] ac = new char[4];
    for (int k = 0; k < 4; k++) {
      int l1 = j >>> 6 * (3 - k) & 0x3f;
      ac[k] = getChar(l1);
    }
    if (s < 1) ac[2] = '=';
    if (s < 2) ac[3] = '=';
    return ac;
  }

  private static char getChar( int i) {
    if (i >= 0 && i <= 25) return (char)(65 + i);
    if (i >= 26 && i <= 51) return (char)(97 + (i - 26));
    if (i >= 52 && i <= 61) return (char)(48 + (i - 52));
    if (i == 62) return '+';
    return i != 63 ? '?' : '/';
  }

  public static byte[] decode(String s) {
    if (s.length() == 0) return new byte[0];
    int i = 0;
    for (int j = s.length() - 1; j > 0 && s.charAt(j) == '='; j--) {
      i++;
    }

    int len = (s.length() * 6) / 8 - i;
    byte[] raw = new byte[len];
    int l = 0;
    for (int i1 = 0; i1 < s.length(); i1 += 4) {
      int j1 = (getValue(s.charAt(i1)) << 18) +
               (getValue(s.charAt(i1 + 1)) << 12) +
               (getValue(s.charAt(i1 + 2)) << 6) +
               (getValue(s.charAt(i1 + 3)));
      for (int k = 0; k < 3 && l + k < raw.length; k++) {
        raw[l + k] = (byte)(j1 >> 8 * (2 - k) & 0xff);
      }
      l += 3;
    }
    return raw;
  }

  private static int getValue(char c) {
    if (c >= 'A' && c <= 'Z') return c - 65;
    if (c >= 'a' && c <= 'z') return (c - 97) + 26;
    if (c >= '0' && c <= '9') return (c - 48) + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return c != '=' ? -1 : 0;
  }
}
