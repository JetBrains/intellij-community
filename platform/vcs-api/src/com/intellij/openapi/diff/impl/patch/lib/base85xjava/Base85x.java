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

/**
 * The main Base85x-java program
 *
 * @author Simon Warta, Kullo
 * @version 0.1
 */
public class Base85x {

  public static byte[] alphabet = {'$', '%', '(', ')', '*', '+', ',', '-', '.', '/',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    ':', ';', '?', '@', 'A', 'B', 'C', 'D', 'E', 'F',
    'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    '[', ']', '^', '_', '`', 'a', 'b', 'c', 'd', 'e',
    'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
    'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
    'z', '{', '|', '}', '~'};


  public static byte[] encode(String data) {
    return encode(data.getBytes());
  }

  public static byte[] decode(String data) {
    return decode(data.getBytes());
  }

  public static byte[] encode(byte[] data) {
    int length = data.length;
    byte[] out = new byte[(length / 4) * 5 + ((length % 4 != 0) ? length % 4 + 1 : 0)];
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

      out[k] = alphabet[c1];
      k++;
      out[k] = alphabet[c2];
      k++;
      out[k] = alphabet[c3];
      k++;
      out[k] = alphabet[c4];
      k++;
      out[k] = alphabet[c5];
      k++;
    }
    if ((rest = length % 4) != 0) {
      int j;
      byte[] block = {'~', '~', '~', '~'};
      for (j = 0; j < rest; j++) {
        block[j] = data[i + j];
      }
      byte[] out_rest = Base85x.encode(block);
      for (j = 0; j < rest + 1; j++) {
        out[k] = out_rest[j];
        k++;
      }
    }
    return out;
  }

  public static byte[] decode(byte[] data) {
    int length = data.length;
    byte[] out = new byte[(length / 5) * 4 + ((length % 5 != 0) ? length % 5 - 1 : 0)];
    int k = 0;
    int rest;
    int i;
    int b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0;
    int b = 0;

    for (i = 0; i + 5 <= length; i += 5) {
      b1 = (int)data[i + 0] & 0xFF;
      b2 = (int)data[i + 1] & 0xFF;
      b3 = (int)data[i + 2] & 0xFF;
      b4 = (int)data[i + 3] & 0xFF;
      b5 = (int)data[i + 4] & 0xFF;

      b1 = ((b1 >= 93) ? b1 - 42 : ((b1 >= 63) ? b1 - 41 : ((b1 >= 40) ? b1 - 38 : b1 - 36)));
      b2 = ((b2 >= 93) ? b2 - 42 : ((b2 >= 63) ? b2 - 41 : ((b2 >= 40) ? b2 - 38 : b2 - 36)));
      b3 = ((b3 >= 93) ? b3 - 42 : ((b3 >= 63) ? b3 - 41 : ((b3 >= 40) ? b3 - 38 : b3 - 36)));
      b4 = ((b4 >= 93) ? b4 - 42 : ((b4 >= 63) ? b4 - 41 : ((b4 >= 40) ? b4 - 38 : b4 - 36)));
      b5 = ((b5 >= 93) ? b5 - 42 : ((b5 >= 63) ? b5 - 41 : ((b5 >= 40) ? b5 - 38 : b5 - 36)));

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
      byte[] block = {'~', '~', '~', '~', '~'};
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
}
