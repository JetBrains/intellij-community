// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.runner.RunWith;

@RunWith(JUnit38AssumeSupportRunner.class)
public class LineSetIncrementalUpdateTest extends UsefulTestCase {
  public void testFuzzUpdate() {
    PropertyChecker.customized()
      .withIterationCount(1_000)
      .checkScenarios(() -> env -> {
        Generator<String> strings = Generator.stringsOf("a \n\r");
        String initialText = env.generateValue(strings, null);
        int start = env.generateValue(Generator.integers(0, initialText.length()), null);
        int end = env.generateValue(Generator.integers(start, initialText.length()), null);
        String replacement = env.generateValue(strings, null);

        env.logMessage("checkIncrementalUpdate(\"" + StringUtil.escapeStringCharacters(initialText) +
                       "\", " + start + ", " + end + ", \"" + StringUtil.escapeStringCharacters(replacement) + "\")");

        checkIncrementalUpdate(initialText, start, end, replacement);
      });
  }

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
    Benchmark.newBenchmark("Document changes in a long line", () -> {
      Document document = new DocumentImpl("a\n" + longLine + "<caret>" + longLine + "\n", true);
      for (int i = 0; i < 1000; i++) {
        int offset = i * 2 + longLine.length();
        assertEquals(1, document.getLineNumber(offset));
        document.insertString(offset, "b");
      }
    }).start();
  }

}