// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public abstract class AbstractMoveElementLeftRightTest extends AbstractEditorTest {
  protected void doTestFromLeftToRight(String leftMostPosition, String... rightPositions) {
    doTest(true, leftMostPosition);
    doTest(false, leftMostPosition, rightPositions);
  }

  protected void doTestFromRightToLeft(String rightMostPosition, String... leftPositions) {
    doTest(false, rightMostPosition);
    doTest(true, rightMostPosition, leftPositions);
  }

  private void doTest(boolean moveLeft, String before, String... after) {
    String current = before;
    for (String next : after) {
      doTestSingle(moveLeft, current, next);
      current = next;
    }
    doTestSingle(moveLeft, current, current);
    if (after.length == 0) return;
    for (int i = after.length - 2; i >= 0; i--) {
      String prev = after[i];
      doTestSingle(!moveLeft, current, prev);
      current = prev;
    }
    doTestSingle(!moveLeft, current, before);
  }
  
  protected void doTestSingle(boolean moveLeft, String before, String after) {
    configureEditor(before);
    executeAction(moveLeft ? IdeActions.MOVE_ELEMENT_LEFT : IdeActions.MOVE_ELEMENT_RIGHT);
    checkResultByText(after);
  }
  
  protected abstract void configureEditor(String contents);
}
