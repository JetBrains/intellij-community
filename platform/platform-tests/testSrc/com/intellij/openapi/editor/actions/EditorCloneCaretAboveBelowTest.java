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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;

import java.io.IOException;

public class EditorCloneCaretAboveBelowTest extends AbstractEditorTest {
  public void testStoringDesiredXPosition() throws IOException {
    init("long line<caret>\n" +
         "line\n" +
         "long line\n" +
         "very long line",
         TestFileType.TEXT);
    cloneCaretBelow();
    checkResultByText("long line<caret>\n" +
                      "line<caret>\n" +
                      "long line\n" +
                      "very long line");
    cloneCaretBelow();
    checkResultByText("long line<caret>\n" +
                      "line<caret>\n" +
                      "long line<caret>\n" +
                      "very long line");
    cloneCaretBelow();
    checkResultByText("long line<caret>\n" +
                      "line<caret>\n" +
                      "long line<caret>\n" +
                      "very long<caret> line");
  }

  public void testCloneAndMove() throws IOException {
    init("long line<caret>\n" +
         "line\n" +
         "long line",
         TestFileType.TEXT);
    cloneCaretBelow();
    moveCaretDown();
    checkResultByText("long line\n" +
                      "line<caret>\n" +
                      "long line<caret>");
  }

  private static void cloneCaretBelow() {
    executeAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW);
  }

  private static void moveCaretDown() {
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
  }
}
