/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

import static org.junit.Assert.assertArrayEquals;

public class MoveLineTest extends AbstractEditorTest {
  public void testInlaysAreMovedWithText() {
    initText("<caret>ab\ncd\n");
    addInlay(1);
    executeAction(IdeActions.ACTION_MOVE_LINE_DOWN_ACTION);
    checkResultByText("cd\n<caret>ab\n");
    assertInlayPositions(4);
  }

  public void testMoveWithMultipleCarets() {
    initText("<caret>foo\nbar\nba<caret>z\nfuz\n");
    executeAction(IdeActions.ACTION_MOVE_LINE_DOWN_ACTION);
    checkResultByText("bar\n<caret>foo\nfuz\nba<caret>z\n");
  }

  public void testMoveWithMultipleCaretsOnTheSameLine() {
    initText("<caret>f<caret>o<caret>o\nbar\nbaz\nfuz\n");
    executeAction(IdeActions.ACTION_MOVE_LINE_DOWN_ACTION);
    checkResultByText("bar\n<caret>f<caret>o<caret>o\nbaz\nfuz\n");
  }

  private void assertInlayPositions(int... offsets) {
    int[] actualPositions = getEditor().getInlayModel().getInlineElementsInRange(0, getEditor().getDocument().getTextLength())
      .stream().mapToInt(i -> i.getOffset()).toArray();
    assertArrayEquals(offsets, actualPositions);
  }
}
