// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.text.StringSearcher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import junit.framework.TestCase;

public class LowLevelSearchUtilTest extends TestCase {
  public void testBackslashBeforeSequence() {
    assertEquals(-1, doTest("n", "\\n"));
  }

  public void testEscapedBackslashBeforeSequence() {
    assertEquals(2, doTest("n", "\\\\n"));
  }

  public void testTwoBackslashesBeforeSequence() {
    assertEquals(-1, doTest("n", "\\\\\\n"));
  }

  public void testBackslashNBeforeSequence() {
    assertEquals(2, doTest("n", "\\nn"));
  }

  public void testBackslashBeforeSequenceNotBeginning() {
    assertEquals(-1, doTest("n", "%d\\n"));
  }

  private static int doTest(String pattern, String text) {
    StringSearcher searcher = new StringSearcher(pattern, true, true, true);
    final int[] index = {-1};
    LowLevelSearchUtil.processTexts(text, 0, text.length(), searcher, value -> {
      index[0] = value;
      return false;
    });
    return index[0];
  }

  public void testProcessTextOccurrencesNeverScansBeyondStartEndOffsetIfNeverAskedTo() {
    StringSearcher searcher = new StringSearcher("xxx", true, true);
    IntList found = new IntArrayList(new int[]{-1});
    CharSequence text = StringUtil.repeat("xxx z ", 1000000);

    PlatformTestUtil.startPerformanceTest("processTextOccurrences", 100, ()-> {
      for (int i=0; i<10000; i++) {
        found.removeInt(0);
        int startOffset = text.length() / 2 + i % 20;
        int endOffset = startOffset + 8;
        boolean success = LowLevelSearchUtil.processTexts(text, startOffset, endOffset, searcher, offset -> {
          found.add(offset);
          return true;
        });
        assertTrue(success);
        assertEquals(startOffset+","+endOffset, 1, found.size());
      }
    }).assertTiming();
  }
}
