// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RangeBuilderTest extends TestCase {
  public void testIdenticalContents() {
    String upToDateContent = "a\na\na\na\n";
    assertTrue(RangesBuilder.createRanges(new DocumentImpl(upToDateContent),
                                          new DocumentImpl(upToDateContent)).isEmpty());
  }

  public void testModified() {
    doTest(
      new String[]{"1", "2", "8", "3", "4"},
      new String[]{"1", "2", "9", "3", "4"},
      new Range[]{new Range(2, 3, 2, 3)}
    );


    doTest(
      new String[]{"1234", "2345", "3456"},
      new String[]{"1234", "23a45", "3456"},
      new Range[]{new Range(1, 2, 1, 2)}
    );

    doTest(
      new String[]{"1234", "2345", "3456"},
      new String[]{"12a34", "2345", "3456"},
      new Range[]{new Range(0, 1, 0, 1)}
    );

    doTest(
      new String[]{"abc"},
      new String[]{"anbnc"},
      new Range[]{new Range(0, 1, 0, 1)}
    );
  }

  public void testDeleted() {
    doTest(new String[]{"a", "a", "a", "b", "b", "b", "c", "c", "c"},
           new String[]{"a", "a", "a", "c", "c", "c"},
           new Range[]{new Range(3, 3, 3, 6)}
    );

    doTest(
      new String[]{"1", "2", "3"},
      new String[]{"1", "2"},
      new Range[]{new Range(2, 2, 2, 3)}
    );

    doTest(
      new String[]{"1", "2", "8", "3", "4"},
      new String[]{"1", "2", "3", "4"},
      new Range[]{new Range(2, 2, 2, 3)}
    );
  }

  public void testInsert() {
    doTest(
      new String[]{"1", "3"},
      new String[]{"1", "2", "3"},
      new Range[]{new Range(1, 2, 1, 1)}
    );

    doTest(
      new String[]{"1", "3"},
      new String[]{"2", "1", "3"},
      new Range[]{new Range(0, 1, 0, 0)}
    );

    doTest(
      new String[]{"1", "2", "3", "4"},
      new String[]{"1", "2", "8", "3", "4"},
      new Range[]{new Range(2, 3, 2, 2)}
    );
  }


  public void testInsertAtEnd() {
    doTest("1",
           "1\n",
           new Range[]{new Range(1, 2, 1, 1)}
    );

    doTest(
      new String[]{"1"},
      new String[]{"1", ""},
      new Range[]{new Range(2, 3, 2, 2)}
    );
  }


  public void testDocument() {
    Document document = new DocumentImpl("1\n\n");
    int lineStartOffset = document.getLineStartOffset(document.getLineCount() - 1);
    assertEquals(3, lineStartOffset);
    document.getLineNumber(2);
  }

  private static void doTest(String[] upToDate, String[] current,
                             Range[] expected) {
    CharSequence upToDateContent = createContentOn(upToDate);
    CharSequence currentContent = createContentOn(current);
    doTest(upToDateContent, currentContent, expected);
  }

  private static void doTest(CharSequence upToDateContent, CharSequence currentContent, Range[] expected) {
    List<Range> result = RangesBuilder.createRanges(new DocumentImpl(currentContent),
                                                    new DocumentImpl(upToDateContent));
    LineStatusTrackerTestUtil.assertEqualRanges(result, Arrays.asList(expected));
  }

  private static String createContentOn(String[] content) {
    return Arrays.stream(content).map(line -> line + '\n').collect(Collectors.joining());
  }
}
