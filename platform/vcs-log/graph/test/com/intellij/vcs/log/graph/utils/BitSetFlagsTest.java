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

import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class BitSetFlagsTest {

  private static char bitToChar(boolean bit) {
    return bit ? '1' : '0';
  }

  private static String toStr(@NotNull Flags flags) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < flags.size(); i++) {
      s.append(bitToChar(flags.get(i)));
    }
    return s.toString();
  }

  @Test
  public void initTest() {
    BitSetFlags flags = new BitSetFlags(5);
    assertEquals("00000", toStr(flags));
    assertEquals(5, flags.size());
  }

  @Test
  public void setTest() {
    BitSetFlags flags = new BitSetFlags(6);
    assertEquals("000000", toStr(flags));

    flags.set(0, true);
    assertEquals("100000", toStr(flags));

    flags.set(0, false);
    assertEquals("000000", toStr(flags));

    flags.set(4, true);
    assertEquals("000010", toStr(flags));

    flags.set(2, true);
    assertEquals("001010", toStr(flags));
  }

  @Test
  public void setAllTest() {
    BitSetFlags flags = new BitSetFlags(6);
    assertEquals("000000", toStr(flags));

    flags.setAll(false);
    assertEquals("000000", toStr(flags));

    flags.setAll(true);
    assertEquals("111111", toStr(flags));
  }

  @Test
  public void size1Test() {
    BitSetFlags flags = new BitSetFlags(1);
    assertEquals("0", toStr(flags));
    assertEquals(1, flags.size());

    flags.set(0, true);
    assertEquals("1", toStr(flags));

    flags.set(0, false);
    assertEquals("0", toStr(flags));

    flags.setAll(true);
    assertEquals("1", toStr(flags));
  }

  @Test
  public void emptyFlagsTest() {
    BitSetFlags flags = new BitSetFlags(0);
    assertEquals(0, flags.size());
    flags.setAll(true);
    assertEquals(0, flags.size());
  }
}
