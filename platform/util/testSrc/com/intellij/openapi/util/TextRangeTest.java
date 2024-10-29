// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import com.intellij.util.text.TextRangeUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class TextRangeTest {
  @Test
  public void substring() {
    assertEquals("abc", new TextRange(0, 3).substring("abc"));
    assertEquals("abc", new TextRange(0, 3).substring("abcd"));
    assertEquals("bc", new TextRange(1, 3).substring("abcd"));
    assertEquals("", new TextRange(2, 2).substring("abcd"));
  }

  @Test
  public void cutOut() {
    assertEquals(new TextRange(1, 5), new TextRange(1, 5).cutOut(new TextRange(0, 4)));
    assertEquals(new TextRange(2, 5), new TextRange(1, 5).cutOut(new TextRange(1, 4)));
    assertEquals(new TextRange(1, 4), new TextRange(1, 5).cutOut(new TextRange(0, 3)));
    assertEquals(new TextRange(3, 3), new TextRange(1, 5).cutOut(new TextRange(2, 2)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void cutOutExc() {
    //noinspection ResultOfMethodCallIgnored
    new TextRange(1, 5).cutOut(new TextRange(1, 10));
  }

  @Test
  public void shiftRight() {
    TextRange range = new TextRange(1, 2);
    assertEquals(new TextRange(2, 3), range.shiftRight(1));
    assertEquals(new TextRange(0, 1), range.shiftRight(-1));
    assertSame(range, range.shiftRight(0));
  }

  @Test
  public void replace() {
    TextRange range = new TextRange(1, 3);
    assertEquals("0a345", range.replace("012345", "a"));
    assertEquals("0345", range.replace("012345", ""));
    assertEquals("0abcdef345", range.replace("012345", "abcdef"));
  }

  @Test
  public void excludedRanges() {
    List<TextRange> excludedRanges =
      Arrays.asList(
        new TextRange(95, 110),
        new TextRange(15, 40),
        new TextRange(5, 20),
        new TextRange(105, 120),
        new TextRange(70, 90),
        new TextRange(50, 57),
        new TextRange(56, 65),
        new TextRange(50, 60)
      );
    List<TextRange> expectedRanges =
      Arrays.asList(
        new TextRange(40, 50),
        new TextRange(65, 70),
        new TextRange(90, 95)
      );
    Iterable<TextRange> result = TextRangeUtil.excludeRanges(new TextRange(30, 100), excludedRanges);
    Iterator<TextRange> resultIterator = result.iterator();
    for (TextRange expectedRange : expectedRanges) {
      assertTrue("Less elements than expected", resultIterator.hasNext());
      TextRange actualRange = resultIterator.next();
      assertEquals("Ranges do not match", expectedRange, actualRange);
    }
    assertTrue("More elements than expected", !resultIterator.hasNext());
  }
}
