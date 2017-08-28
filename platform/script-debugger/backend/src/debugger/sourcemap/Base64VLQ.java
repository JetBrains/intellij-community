/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.debugger.sourcemap;

final class Base64VLQ {
  private Base64VLQ() {
  }

  interface CharIterator {
    boolean hasNext();
    char next();
  }

  // A Base64 VLQ digit can represent 5 bits, so it is base-32.
  private static final int VLQ_BASE_SHIFT = 5;
  private static final int VLQ_BASE = 1 << VLQ_BASE_SHIFT;

  // A mask of bits for a VLQ digit (11111), 31 decimal.
  private static final int VLQ_BASE_MASK = VLQ_BASE - 1;

  // The continuation bit is the 6th bit.
  private static final int VLQ_CONTINUATION_BIT = VLQ_BASE;

  /**
   * Decodes the next VLQValue from the provided CharIterator.
   */
  public static int decode(CharIterator in) {
    int result = 0;
    int shift = 0;
    int digit;
    do {
      digit = Base64.BASE64_DECODE_MAP[in.next()];
      assert (digit != -1) : "invalid char";

      result += (digit & VLQ_BASE_MASK) << shift;
      shift += VLQ_BASE_SHIFT;
    }
    while ((digit & VLQ_CONTINUATION_BIT) != 0);

    boolean negate = (result & 1) == 1;
    result >>= 1;
    return negate ? -result : result;
  }

  private static final class Base64 {
    /**
     * A map used to convert integer values in the range 0-63 to their base64
     * values.
     */
    private static final String BASE64_MAP =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "abcdefghijklmnopqrstuvwxyz" +
      "0123456789+/";

    /**
     * A map used to convert base64 character into integer values.
     */
    private static final int[] BASE64_DECODE_MAP = new int[256];

    static {
      for (int i = 0; i < BASE64_MAP.length(); i++) {
        BASE64_DECODE_MAP[BASE64_MAP.charAt(i)] = i;
      }
    }
  }
}