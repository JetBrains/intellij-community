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
  
  private void doTestSingle(boolean moveLeft, String before, String after) {
    configureEditor(before);
    executeAction(moveLeft ? IdeActions.MOVE_ELEMENT_LEFT : IdeActions.MOVE_ELEMENT_RIGHT);
    checkResultByText(after);
  }
  
  protected abstract void configureEditor(String contents);
}
