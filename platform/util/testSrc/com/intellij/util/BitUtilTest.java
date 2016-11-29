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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitUtilTest {
  @Test
  public void byteFlags() {
    byte flags = 0;
    for (int i = 0; i < 8; i++) {
      byte mask = (byte)(1 << i);
      assertFalse(BitUtil.isSet(flags, mask));

      flags = BitUtil.set(flags, mask, true);
      assertTrue(BitUtil.isSet(flags, mask));

      flags = BitUtil.clear(flags, mask);
      assertFalse(BitUtil.isSet(flags, mask));
    }
  }

  @Test
  public void intFlags() {
    int flags = 0;
    for (int i = 0; i < 32; i++) {
      int mask = 1 << i;
      assertFalse(BitUtil.isSet(flags, mask));

      flags = BitUtil.set(flags, mask, true);
      assertTrue(BitUtil.isSet(flags, mask));

      flags = BitUtil.clear(flags, mask);
      assertFalse(BitUtil.isSet(flags, mask));
    }
  }

  @Test
  public void longFlags() {
    long flags = 0;
    for (int i = 0; i < 64; i++) {
      long mask = 1L << i;
      assertFalse(BitUtil.isSet(flags, mask));

      flags = BitUtil.set(flags, mask, true);
      assertTrue(BitUtil.isSet(flags, mask));

      flags = BitUtil.clear(flags, mask);
      assertFalse(BitUtil.isSet(flags, mask));
    }
  }
}