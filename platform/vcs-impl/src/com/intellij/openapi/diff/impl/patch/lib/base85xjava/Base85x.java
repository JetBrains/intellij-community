/*
 * Copyright (c) 2012 Simon Warta
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.diff.impl.patch.lib.base85xjava;

import java.util.Arrays;

/**
 * The main Base85x-java program
 *
 * @author Simon Warta, Kullo, Nadya Zabrodina
 * @version 0.2
 */
public class Base85x {

  private static final int ASCII_LEFT_SHIFT = 33;
  private static final int ASCII_RIGHT_SHIFT = 127;

  private static final char[] ALPHABET_85 = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
    'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
    'U', 'V', 'W', 'X', 'Y', 'Z',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
    'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
    'u', 'v', 'w', 'x', 'y', 'z',
    '!', '#', '$', '%', '&', '(', ')', '*', '+', '-',
    ';', '<', '=', '>', '?', '@', '^', '_', '`', '{',
    '|', '}', '~'
  };

  private static final int[] INDEX_OF = initIndexOfChar();

  private static int[] initIndexOfChar() {
    int[] result = new int[256];
    Arrays.fill(result, -1);
    for (int i = 0; i < ALPHABET_85.length; i++) {
      result[ALPHABET_85[i]] = i;
    }
    return result;
  }

  public static char encodeChar(int i) throws Base85FormatException {
    if (i < 0 || i >= ALPHABET_85.length) {
      throw new Base85FormatException("Wrong index to encode as char " + i);
    }
    return ALPHABET_85[i];
  }

  public static int decodeChar(char c) throws Base85FormatException {
    // optimization for 2-byte char size
    if (c < ASCII_LEFT_SHIFT || c > ASCII_RIGHT_SHIFT) {
      throw new Base85FormatException("Illegal char " + (int)c);
    }
    int result = INDEX_OF[(int)c];
    if (result == -1) {
      throw new Base85FormatException("Illegal char " + (int)c);
    }
    return result;
  }

  public static byte[] decode(String data) throws Base85FormatException {
    return decode(data.toCharArray());
  }

  public static char[] encode(byte[] data, int length) throws Base85FormatException {
    char[] out = new char[(length / 4) * 5 + ((length % 4 != 0) ? length % 4 + 1 : 0)];
    int k = 0;
    // 64 bit integer
    long b;
    int c1, c2, c3, c4, c5;
    int rest;
    int i;

    for (i = 0; i + 4 <= length; i += 4) {
      b = 0L;
      b |= (int)data[i] & 0xFF;
      b <<= 8;
      b |= (int)data[i + 1] & 0xFF;
      b <<= 8;
      b |= (int)data[i + 2] & 0xFF;
      b <<= 8;
      b |= (int)data[i + 3] & 0xFF;

      c5 = (int)(b % 85);
      b /= 85;
      c4 = (int)(b % 85);
      b /= 85;
      c3 = (int)(b % 85);
      b /= 85;
      c2 = (int)(b % 85);
      b /= 85;
      c1 = (int)(b % 85);

      out[k] = encodeChar(c1);
      k++;
      out[k] = encodeChar(c2);
      k++;
      out[k] = encodeChar(c3);
      k++;
      out[k] = encodeChar(c4);
      k++;
      out[k] = encodeChar(c5);
      k++;
    }
    if ((rest = length % 4) != 0) {
      int j;
      byte[] block = {'~', '~', '~', '~'};
      for (j = 0; j < rest; j++) {
        block[j] = data[i + j];
      }
      char[] out_rest = encode(block, block.length);
      for (j = 0; j < rest + 1; j++) {
        out[k] = out_rest[j];
        k++;
      }
    }
    return out;
  }

  public static byte[] decode(char[] data) throws Base85FormatException {
    int length = data.length;
    byte[] out = new byte[(length / 5) * 4 + ((length % 5 != 0) ? length % 5 - 1 : 0)];
    int k = 0;
    int rest;
    int i;
    int b1, b2, b3, b4, b5;
    int b;

    for (i = 0; i + 5 <= length; i += 5) {
      b1 = decodeChar(data[i]);
      b2 = decodeChar(data[i + 1]);
      b3 = decodeChar(data[i + 2]);
      b4 = decodeChar(data[i + 3]);
      b5 = decodeChar(data[i + 4]);

      // overflow into negative numbers
      // is normal and does not do any damage because
      // of the cut operations below
      b = b1 * 52200625 + b2 * 614125 + b3 * 7225 + b4 * 85 + b5;

      out[k] = (byte)((b >>> 24) & 0xFF);
      k++;
      out[k] = (byte)((b >>> 16) & 0xFF);
      k++;
      out[k] = (byte)((b >>> 8) & 0xFF);
      k++;
      out[k] = (byte)(b & 0xFF);
      k++;
    }

    if ((rest = length % 5) != 0) {
      int j;
      char[] block = {'~', '~', '~', '~', '~'};
      for (j = 0; j < rest; j++) {
        block[j] = data[i + j];
      }
      byte[] out_rest = decode(block);
      for (j = 0; j < rest - 1; j++) {
        out[k] = out_rest[j];
        k++;
      }
    }

    return out;
  }

  public static class Base85FormatException extends Exception {
    Base85FormatException(String s) {
      super(s);
    }
  }
}
