/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.diff;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * @author dyoma
 */
public class ReindexerTest extends TestCase {
  private Reindexer myReindexer = new Reindexer();

  @Override
  protected void tearDown() throws Exception {
    myReindexer = null;
    super.tearDown();
  }

  public void testNoUnique() {
    int[] ints1 = new int[]{1, 2, 3};
    int[] ints2 = new int[]{3, 2, 1, 2, 2};
    int[][] reindexed = myReindexer.discardUnique(ints1, ints2);
    assertTrue(Arrays.equals(ints1, reindexed[0]));
    assertTrue(Arrays.equals(ints2, reindexed[1]));
    for (int i = 0; i < 3; i++) {
      assertEquals(i, myReindexer.restoreIndex(i, 0));
      assertEquals(i, myReindexer.restoreIndex(i, 1));
    }
    assertEquals(3, myReindexer.restoreIndex(3, 1));
    assertEquals(4, myReindexer.restoreIndex(4, 1));
  }

  public void testSomeUniqu() {
    int[][] reindexed = myReindexer.discardUnique(new int[]{1, 2, 3}, new int[]{3, 4, 1, 3});
    assertTrue(Arrays.equals(reindexed[0], new int[]{1, 3}));
    assertTrue(Arrays.equals(reindexed[1], new int[]{3, 1, 3}));
    assertEquals(0, myReindexer.restoreIndex(0, 0));
    assertEquals(0, myReindexer.restoreIndex(0, 1));
    assertEquals(2, myReindexer.restoreIndex(1, 0));
    assertEquals(2, myReindexer.restoreIndex(1, 1));
    assertEquals(3, myReindexer.restoreIndex(2, 1));
  }

  public void testAllUnique() {
    int[][] reindexed = myReindexer.discardUnique(new int[]{1, 2, 3}, new int[]{4, 5});
    assertEquals(0, reindexed[0].length);
    assertEquals(0, reindexed[1].length);
  }
}
