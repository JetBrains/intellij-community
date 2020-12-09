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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.Interval;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class EditorUtilTest extends LightPlatformCodeInsightTestCase {
  public void testGetNotFoldedLineStartEndOffsets() {
    configureFromFileText(getTestName(false) + ".txt",
                          "aaa\nbbb\nccc\nddd");
    EditorTestUtil.addFoldRegion(getEditor(), 4, 8, "...", true);

    assertVisualLineRange(2, 0, 3);
    assertVisualLineRange(4, 4, 11);
    assertVisualLineRange(7, 4, 11);
    assertVisualLineRange(8, 4, 11);
    assertVisualLineRange(9, 4, 11);
    assertVisualLineRange(13, 12, 15);
  }

  public void testScrollFromBeginToEnd() {
    createSmallEditor("<caret>12345\n67890");
    EditorUtil.scrollToTheEnd(getEditor());
    // caret should be at the end, but editor shouldn't be scrolled to it
    assertCaretAt(1, 5, false);
  }

  public void testScrollFromLastLineToEnd() {
    createSmallEditor("12345\n<caret>12345");
    EditorUtil.scrollToTheEnd(getEditor());
    // caret should be at the end, and visible
    assertCaretAt(1, 5, true);
  }

  public void testScrollToEndWithEmptyLastLine() {
    createSmallEditor("12345\n12345<caret>\n");
    Rectangle oldVisibleArea = getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished();
    EditorUtil.scrollToTheEnd(getEditor(), true);

    // caret should be at the end, but only vertical scrolling should happen
    assertCaretAt(2, 0, false);
    Rectangle newVisibleArea = getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished();
    assertEquals(oldVisibleArea.x, newVisibleArea.x);
    assertTrue(oldVisibleArea.y < newVisibleArea.y);
  }

  public void testLogicalLineToYRange() {
    createEditor("line1\nline2\nlong long line\n");
    EditorTestUtil.configureSoftWraps(getEditor(), 10);
    int lineHeight = getEditor().getLineHeight();

    @NotNull Pair<@NotNull Interval, @Nullable Interval> p1 = EditorUtil.logicalLineToYRange(getEditor(), 1);
    assertEquals(lineHeight, p1.first.intervalStart());
    assertEquals(lineHeight * 2, p1.first.intervalEnd());
    assertEquals(lineHeight, p1.second.intervalStart());
    assertEquals(lineHeight * 2, p1.second.intervalEnd());

    @NotNull Pair<@NotNull Interval, @Nullable Interval> p2 = EditorUtil.logicalLineToYRange(getEditor(), 2);
    assertEquals(lineHeight * 2, p2.first.intervalStart());
    assertEquals(lineHeight * 4, p2.first.intervalEnd());
    assertEquals(lineHeight * 2, p2.second.intervalStart());
    assertEquals(lineHeight * 4, p2.second.intervalEnd());
  }

  public void testLogicalLineToYRangeShared() {
    createEditor("line1\nline2\n");
    EditorTestUtil.addFoldRegion(getEditor(), 5, 6, "...", true);

    @NotNull Pair<@NotNull Interval, @Nullable Interval> p = EditorUtil.logicalLineToYRange(getEditor(), 1);
    assertEquals(0, p.first.intervalStart());
    assertEquals(getEditor().getLineHeight(), p.first.intervalEnd());
    assertNull(p.second);
  }

  public void testYToLogicalLineRange() {
    createEditor("line1\nline2\nline3\n");
    EditorTestUtil.addFoldRegion(getEditor(), 5, 6, "...", true);
    int lineHeight = getEditor().getLineHeight();

    Interval i1 = EditorUtil.yToLogicalLineRange(getEditor(), lineHeight / 2);
    assertEquals(0, i1.intervalStart());
    assertEquals(1, i1.intervalEnd());

    Interval i2 = EditorUtil.yToLogicalLineRange(getEditor(), lineHeight * 3 / 2);
    assertEquals(2, i2.intervalStart());
    assertEquals(2, i2.intervalEnd());
  }

  private void createEditor(String text) {
    configureFromFileText(getTestName(false) + ".txt", text);
  }

  private void createSmallEditor(String text) {
    configureFromFileText(getTestName(false) + ".txt", text);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 4, 2);
  }

  private void assertCaretAt(int line, int column, boolean isVisible) {
    LogicalPosition position = getEditor().getCaretModel().getLogicalPosition();
    assertEquals(line, position.line);
    assertEquals(column, position.column);
    Point caretXY = getEditor().logicalPositionToXY(position);
    assertEquals(isVisible, getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished().contains(caretXY));
  }

  private void assertVisualLineRange(int offset, int lineStartOffset, int lineEndOffset) {
    assertEquals(lineStartOffset, EditorUtil.getNotFoldedLineStartOffset(getEditor(), offset));
    assertEquals(lineEndOffset, EditorUtil.getNotFoldedLineEndOffset(getEditor(), offset));
  }
}