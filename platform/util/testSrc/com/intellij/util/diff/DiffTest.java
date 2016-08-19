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

import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

import java.util.ArrayList;

/**
 * @author dyoma
 */
public class DiffTest extends TestCase {
  private static final Object[] DATA_123 = {"1", "2", "3"};
  private static final Object[] DATA_AB = {"a", "b"};
  private static final Object[] DATA_12AB23 = {"1", "2", "a", "b", "2", "3"};
  private static final Object[] DATA_123_ = {"x","y","z","1", "2","3","alpha","beta"};
  private static final Object[] DATA_12AB23_ = {"x","y","z","1", "2", "a", "b", "2", "3","alpha","beta"};

  public void testEqual() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_123, DATA_123);
    assertNull(change);
  }

  public void testCompletelyDifferent() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_AB, DATA_123);
    IntLCSTest.checkLastChange(change, 0, 0, 3, 2);
  }

  public void testSameMiddle() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_123, new Object[]{"a", "2", "b"});
    IntLCSTest.checkChange(change, 0, 0, 1, 1);
    IntLCSTest.checkLastChange(change.link, 2, 2, 1, 1);
  }

  public void testOverlap() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_123, DATA_12AB23);
    IntLCSTest.checkLastChange(change, 2, 2, 3, 0); // inserted:           AB2
  }

  public void testTrim() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_123_, DATA_12AB23_);
    IntLCSTest.checkLastChange(change, 5, 5, 3, 0); // inserted:           AB2
  }
  
  public void testEqualUpToOneEnd() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_AB, new Object[]{"a", "b", "1"});
    IntLCSTest.checkLastChange(change, 2, 2, 1, 0);
  }

  public void testEmptyAgainstSmth() throws FilesTooBigForDiffException {
    Object[] empty = ArrayUtil.EMPTY_OBJECT_ARRAY;
    Diff.Change change = Diff.buildChanges(empty, empty);
    assertNull(change);
    change = Diff.buildChanges(DATA_AB, empty);
    IntLCSTest.checkLastChange(change, 0, 0, 0, 2);
    change = Diff.buildChanges(empty, DATA_123);
    IntLCSTest.checkLastChange(change, 0, 0, 3, 0);
  }

  public void testPerformance() throws FilesTooBigForDiffException {
    ArrayList<String> first = new ArrayList<>();
    ArrayList<String> second = new ArrayList<>();
    int max = 1000;
    for (int i = 0; i < max; i++) {
      first.add(Integer.toString(i));
      second.add(Integer.toString(max - i - 1));
    }
    Diff.buildChanges(first.toArray(), second.toArray());
    long start = System.currentTimeMillis();
    Diff.buildChanges(first.toArray(), second.toArray());
    System.out.println("Duration: " +(System.currentTimeMillis() - start));
  }

}
