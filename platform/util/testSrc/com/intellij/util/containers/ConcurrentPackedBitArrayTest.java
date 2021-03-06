/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.containers;

import junit.framework.TestCase;

public class ConcurrentPackedBitArrayTest extends TestCase {
  public void test() {
    ConcurrentPackedBitsArray bitSet = ConcurrentPackedBitsArray.create(4);
    int N = 3000;
    for (int i=0; i<N;i++) {
      assertEquals(0, bitSet.get(i) & 0xf);
      bitSet.set(i, 0xa);
      assertEquals(0xa, bitSet.get(i) & 0xf);
      bitSet.set(i, 0x2);
      assertEquals(0x2, bitSet.get(i) & 0xf);
    }

    try {
      bitSet.set(1, 0x10);
      fail("must throw IAE");
    }
    catch (IllegalArgumentException ignored) {
    }
    try {
      bitSet.set(1, -1);
      fail("must throw IAE");
    }
    catch (IllegalArgumentException ignored) {
    }
    try {
      ConcurrentPackedBitsArray.create(65);
      fail("must throw IAE");
    }
    catch (IllegalArgumentException ignored) {
    }
    try {
      ConcurrentPackedBitsArray.create(0);
      fail("must throw IAE");
    }
    catch (IllegalArgumentException ignored) {
    }
  }

  public void testChunkSize32() {
    ConcurrentPackedBitsArray bitSet = ConcurrentPackedBitsArray.create(32);
    bitSet.set(0, 0xDEAFBEEFL);
    assertEquals(0xDEAFBEEFL, bitSet.get(0) & 0xFFFFFFFFL);
    
    bitSet = ConcurrentPackedBitsArray.create(31);
    long eadBeef = 0b0101_1110_1010_1111_1011_1110_1110_1111L;
    bitSet.set(0, eadBeef);
    assertEquals(eadBeef, bitSet.get(0));

    try {
      bitSet.set(0, 0xDEAFBEEFL);
      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }
}
