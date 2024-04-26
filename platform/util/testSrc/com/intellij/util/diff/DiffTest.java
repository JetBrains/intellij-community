// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.diff;

import com.intellij.util.ArrayUtilRt;
import junit.framework.TestCase;
import org.junit.Assert;

public class DiffTest extends TestCase {
  private static final Object[] DATA_123 = {"1", "2", "3"};
  private static final Object[] DATA_AB = {"a", "b"};
  private static final Object[] DATA_12AB23 = {"1", "2", "a", "b", "2", "3"};
  private static final Object[] DATA_123_ = {"x","y","z","1", "2","3","alpha","beta"};
  private static final Object[] DATA_12AB23_ = {"x","y","z","1", "2", "a", "b", "2", "3","alpha","beta"};
  private static final Object[] DATA_12nullABnull23 = {"1", "2", null, "a", "b", null, "2", "3"};
  private static final Object[] DATA_null12AB23 = {null, "1", "2", "a", "b", "2", "3"};

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
    Object[] empty = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    Diff.Change change = Diff.buildChanges(empty, empty);
    assertNull(change);
    change = Diff.buildChanges(DATA_AB, empty);
    IntLCSTest.checkLastChange(change, 0, 0, 0, 2);
    change = Diff.buildChanges(empty, DATA_123);
    IntLCSTest.checkLastChange(change, 0, 0, 3, 0);
  }

  public void testNulls() throws FilesTooBigForDiffException {
    Diff.Change change = Diff.buildChanges(DATA_12nullABnull23, DATA_null12AB23);
    int idx = 0;
    while (change != null) {
      switch (idx) {
        case 0 -> IntLCSTest.checkChange(change, 0, 0, 1, 0);
        case 1 -> IntLCSTest.checkChange(change, 2, 3, 0, 1);
        case 2 -> IntLCSTest.checkChange(change, 5, 5, 0, 1);
      }
      change = change.link;
      idx++;
    }
    Assert.assertEquals(3, idx);
  }
}
