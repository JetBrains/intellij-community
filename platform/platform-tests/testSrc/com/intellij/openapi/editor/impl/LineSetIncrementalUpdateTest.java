// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;

public class LineSetIncrementalUpdateTest extends UsefulTestCase {
  public void testSlashRIssues() {
    checkIncrementalUpdate("\n\r", 0, 0, "");
    checkIncrementalUpdate("\r\n", 0, 1, "");
    checkIncrementalUpdate("\r", 0, 0, "\r");
  }

  public void testClearSingleLineEnd() {
    checkIncrementalUpdate("\n", 0, 1, "");
  }

  private static void checkIncrementalUpdate(String initialText, int start, int end, String replacement) {
    CharSequence newText = StringUtil.replaceSubSequence(initialText, start, end, replacement);

    LineSet initial = LineSet.createLineSet(initialText);
    LineSet updated = initial.update(initialText, start, end, replacement, false);
    LineSet fresh = LineSet.createLineSet(newText);

    assertEquals("line count", fresh.getLineCount(), updated.getLineCount());
    for (int i = 0; i < updated.getLineCount(); i++) {
      assertEquals("line start " + i, fresh.getLineStart(i), updated.getLineStart(i));
      assertEquals("line end " + i, fresh.getLineEnd(i), updated.getLineEnd(i));
      assertEquals("line feed length " + i, fresh.getSeparatorLength(i), updated.getSeparatorLength(i));
    }
  }

  public void testTypingInLongLinePerformance() {
    String longLine = StringUtil.repeat("a ", 200000);
    PlatformTestUtil.startPerformanceTest("Document changes in a long line", 1000, () -> {
      Document document = new DocumentImpl("a\n" + longLine + "<caret>" + longLine + "\n", true);
      for (int i = 0; i < 1000; i++) {
        int offset = i * 2 + longLine.length();
        assertEquals(1, document.getLineNumber(offset));
        document.insertString(offset, "b");
      }
    }).assertTiming();
  }

}