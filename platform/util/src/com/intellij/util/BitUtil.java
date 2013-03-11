/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @since 27.03.2012
 */
public class BitUtil {
  public static boolean isSet(final byte value, final byte mask) {
    return (value & mask) == mask;
  }

  public static boolean isSet(final int value, final int mask) {
    return (value & mask) == mask;
  }

  public static boolean notSet(final int value, final int mask) {
    return (value & mask) != mask;
  }

  /**
   * @return {@code value} with the bit corresponding to the {@code mask} set (if setBit is true) or cleared (if setBit is false)
   */
  public static int set(int value, int mask, boolean setBit) {
    return setBit ? value | mask : value & ~mask;
  }

  /**
   * @return {@code value} with the bit corresponding to the {@code mask} set (if setBit is true) or cleared (if setBit is false)
   */
  public static byte set(byte value, byte mask, boolean setBit) {
    return (byte)(setBit ? value | mask : value & ~mask);
  }
}
