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
package com.intellij.util.diff;

import junit.framework.TestCase;

import java.util.BitSet;
import java.util.Random;

public class ReindexerNewTest extends TestCase {
  public void testAllUnique() {
    checkCase(new int[]{1, 2, 3}, new int[]{4, 5});
    checkCase(new int[]{}, new int[]{4, 5});
    checkCase(new int[]{1, 2, 3}, new int[]{});
  }

  public void testUniqueBeginning() {
    checkCase(new int[]{13, 15, 1, 2, 3}, new int[]{17, 1, 3});
  }

  public void testUniqueEnd() {
    checkCase(new int[]{1, 2, 3, 13, 15}, new int[]{1, 3, 17});
  }

  public void testSingleUniqueMoved() {
    checkCase(new int[]{1, 1, 2, 2, 10}, new int[]{10, 1, 1, 2, 2});
  }

  public void testSingleFunctionMoved() {
    checkCase(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 7, 9, 10, 2, 11, 12, 5, 13, 7, 8, 7},
              new int[]{10, 2, 11, 12, 5, 13, 7, 8, 7, 9, 1, 2, 3, 4, 5, 6, 7, 8, 7});
  }

  public void testInnerChunks() {
    checkCase(new int[]{0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0}, new int[]{1, 2, 1, 3, 1, 4, 1, 5, 1, 6, 1, 7, 1});
    checkCase(new int[]{0, 2, 3, 0, 4, 5, 0}, new int[]{1, 2, 1, 3, 4, 5, 1});
    checkCase(new int[]{15, 1, 2, 3, 1, 4, 5, 1, 15}, new int[]{13, 1, 2, 1, 3, 4, 5, 1, 13});
    checkCase(new int[]{15, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 15}, new int[]{13, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 13});
  }

  public void testRandomExample() {
    int[] ints1 = new int[100];
    int[] ints2 = new int[100];
    Random rng = new Random();
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 100; j++) {
        ints1[j] = rng.nextInt(60);
        ints2[j] = rng.nextInt(60);
      }

      StringBuilder builder = new StringBuilder();
      builder.append("{");
      for (int j = 0; j < 100; j++) {
        builder.append(ints1[j]).append(", ");
      }
      builder.append("}\n{");
      for (int j = 0; j < 100; j++) {
        builder.append(ints2[j]).append(", ");
      }
      builder.append("}\n");

      checkCase(ints1, ints2, builder.toString());
    }
  }

  public static void checkCase(int[] ints1, int[] ints2) {
    checkCase(ints1, ints2, "");
  }

  public static void checkCase(int[] ints1, int[] ints2, String message) {
    final BitSet[] reindexChanges = new BitSet[]{new BitSet(), new BitSet()};
    LCSBuilder builder = new LCSBuilder() {
      private int myIndex1 = 0;
      private int myIndex2 = 0;

      @Override
      public void addEqual(int length) {
        myIndex1 += length;
        myIndex2 += length;
      }

      @Override
      public void addChange(int first, int second) {
        reindexChanges[0].set(myIndex1, myIndex1 + first);
        reindexChanges[1].set(myIndex2, myIndex2 + second);

        myIndex1 += first;
        myIndex2 += second;
      }
    };

    Reindexer reindexer = new Reindexer();
    int[][] discarded = reindexer.discardUnique(ints1, ints2);
    MyersLCS lcs = new MyersLCS(discarded[0], discarded[1]);
    lcs.execute();
    reindexer.reindex(lcs.getChanges(), builder);

    IntLCSAutoTest.verifyLCS(ints1, ints2, reindexChanges[0], reindexChanges[1]);
  }
}
