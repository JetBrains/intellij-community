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
package com.intellij.vcs.log.graph.utils;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnsignedBitSetTest {

  private static String toStr(@NotNull UnsignedBitSet bitSet) {
    StringBuilder s = new StringBuilder();
    for (int i = -5; i <= 5; i++) {
      if (bitSet.get(i)) {
        s.append(1);
      }
      else {
        s.append(0);
      }
    }
    return s.toString();
  }

  @Test
  public void initFalse() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    assertEquals("00000000000", toStr(bitSet));
  }

  @Test
  public void setZero() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(0, true);
    assertEquals("00000100000", toStr(bitSet));
  }

  @Test
  public void setOne() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(1, true);
    assertEquals("00000010000", toStr(bitSet));
  }

  @Test
  public void setMinusOne() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(-1, true);
    assertEquals("00001000000", toStr(bitSet));
  }

  @Test
  public void setSeveralTimes() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(3, true);
    bitSet.set(-3, true);
    bitSet.set(-2, true);
    assertEquals("00110000100", toStr(bitSet));
  }

  @Test
  public void setPositiveRange() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(0, 4, true);
    assertEquals("00000111110", toStr(bitSet));
  }

  @Test
  public void setNegativeRange() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(-3, -1, true);
    assertEquals("00111000000", toStr(bitSet));
  }

  @Test
  public void setRange() {
    UnsignedBitSet bitSet = new UnsignedBitSet();
    bitSet.set(-3, 1, true);
    assertEquals("00111110000", toStr(bitSet));
  }

}