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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IterationStateTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testBlockSelection() {
    verifySplitting("aa,<block>bb\ncc,d</block>d",
                    new Segment(0, 3, Color.BLACK),
                    new Segment(3, 4, Color.WHITE),
                    new Segment(4, 5, Color.BLACK),
                    new Segment(5, 6, Color.BLACK),
                    new Segment(6, 9, Color.BLACK),
                    new Segment(9, 10, Color.WHITE),
                    new Segment(10, 11, Color.BLACK));
  }

  public void testMultiCaretBlockSelection() {
    EditorTestUtil.enableMultipleCarets();
    try {
      testBlockSelection();
    }
    finally {
      EditorTestUtil.disableMultipleCarets();
    }
  }

  private void verifySplitting(String text, Segment... expectedSegments) {
    myFixture.configureByText(PlainTextFileType.INSTANCE, text);
    EditorEx editor = (EditorEx)myFixture.getEditor();
    IterationState iterationState = new IterationState(editor, 0, editor.getDocument().getTextLength(), true);
    try {
      List<Segment> actualSegments = new ArrayList<Segment>();
      do {
        actualSegments.add(new Segment(iterationState.getStartOffset(),
                                       iterationState.getEndOffset(),
                                       iterationState.getMergedAttributes().getForegroundColor()));
        iterationState.advance();
      }
      while (!iterationState.atEnd());

      Assert.assertArrayEquals(expectedSegments, actualSegments.toArray());
    }
    finally {
      iterationState.dispose();
    }
  }

  private static class Segment {
    private final int start;
    private final int end;
    private final Color fgColor;

    private Segment(int start, int end, @NotNull Color fgColor) {
      this.start = start;
      this.end = end;
      this.fgColor = fgColor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Segment segment = (Segment)o;

      if (end != segment.end) return false;
      if (start != segment.start) return false;
      if (!fgColor.equals(segment.fgColor)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = start;
      result = 31 * result + end;
      result = 31 * result + fgColor.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "Segment{" +
             "start=" + start +
             ", end=" + end +
             ", color=" + fgColor +
             '}';
    }
  }
}
