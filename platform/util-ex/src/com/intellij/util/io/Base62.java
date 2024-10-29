// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import org.jetbrains.annotations.ApiStatus;

/**
 * A Base62 encoder/decoder.
 * <p>
 * https://github.com/seruco/base62/blob/master/LICENSE
 *
 * @author Sebastian Ruhleder, sebastian@seruco.io
 */
@ApiStatus.Internal
public final class Base62 {
  private static final int STANDARD_BASE = 256;
  private static final int TARGET_BASE = 62;

  private static final byte[] lookup = new byte[256];

  private static final byte[] alphabet = {
    (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6', (byte)'7',
    (byte)'8', (byte)'9', (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F',
    (byte)'G', (byte)'H', (byte)'I', (byte)'J', (byte)'K', (byte)'L', (byte)'M', (byte)'N',
    (byte)'O', (byte)'P', (byte)'Q', (byte)'R', (byte)'S', (byte)'T', (byte)'U', (byte)'V',
    (byte)'W', (byte)'X', (byte)'Y', (byte)'Z', (byte)'a', (byte)'b', (byte)'c', (byte)'d',
    (byte)'e', (byte)'f', (byte)'g', (byte)'h', (byte)'i', (byte)'j', (byte)'k', (byte)'l',
    (byte)'m', (byte)'n', (byte)'o', (byte)'p', (byte)'q', (byte)'r', (byte)'s', (byte)'t',
    (byte)'u', (byte)'v', (byte)'w', (byte)'x', (byte)'y', (byte)'z'
  };

  static {
    for (int i = 0; i < alphabet.length; i++) {
      lookup[alphabet[i]] = (byte)(i & 0xFF);
    }
  }

  /**
   * Encodes a sequence of bytes in Base62 encoding.
   *
   * @param message a byte sequence.
   * @return a sequence of Base62-encoded bytes.
   */
  public static byte[] encode(byte[] message) {
    byte[] indices = convert(message, STANDARD_BASE, TARGET_BASE);
    return translate(indices, alphabet);
  }

  /**
   * Decodes a sequence of Base62-encoded bytes.
   *
   * @param encoded a sequence of Base62-encoded bytes.
   * @return a byte sequence.
   */
  public static byte[] decode(final byte[] encoded) {
    byte[] prepared = translate(encoded, lookup);
    return convert(prepared, TARGET_BASE, STANDARD_BASE);
  }

  /**
   * Uses the elements of a byte array as indices to a dictionary and returns the corresponding values
   * in form of a byte array.
   */
  private static byte[] translate(byte[] indices, byte[] dictionary) {
    byte[] translation = new byte[indices.length];
    for (int i = 0; i < indices.length; i++) {
      translation[i] = dictionary[indices[i]];
    }
    return translation;
  }

  /**
   * Converts a byte array from a source base to a target base using the alphabet.
   * <p>
   * This algorithm is inspired by: http://codegolf.stackexchange.com/a/21672
   */
  private static byte[] convert(byte[] message, int sourceBase, int targetBase) {
    int estimatedLength = estimateOutputLength(message.length, sourceBase, targetBase);
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(estimatedLength);
    byte[] source = message;
    while (source.length > 0) {
      BufferExposingByteArrayOutputStream quotient = new BufferExposingByteArrayOutputStream(source.length);

      int remainder = 0;

      for (byte b : source) {
        int accumulator = (b & 0xFF) + remainder * sourceBase;
        int digit = (accumulator - (accumulator % targetBase)) / targetBase;

        remainder = accumulator % targetBase;

        if (quotient.size() > 0 || digit > 0) {
          quotient.write(digit);
        }
      }

      out.write(remainder);

      source = quotient.toByteArray();
    }

    // pad output with zeroes corresponding to the number of leading zeroes in the message
    for (int i = 0; i < message.length - 1 && message[i] == 0; i++) {
      out.write(0);
    }

    int length = out.size();
    byte[] array = out.getInternalBuffer();
    int i = 0;
    int j = length - 1;
    while (j > i) {
      byte tmp = array[j];
      array[j] = array[i];
      array[i] = tmp;
      j--;
      i++;
    }
    return array;
  }

  /**
   * Estimates the length of the output in bytes.
   */
  private static int estimateOutputLength(int inputLength, int sourceBase, int targetBase) {
    return (int)Math.ceil((Math.log(sourceBase) / Math.log(targetBase)) * inputLength);
  }
}
