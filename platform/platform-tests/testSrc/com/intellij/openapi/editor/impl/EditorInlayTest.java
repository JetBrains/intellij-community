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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;

public class EditorInlayTest extends AbstractEditorTest {
  public void testCaretMovement() throws Exception {
    initText("ab");
    addInlay(1);
    right();
    checkCaretPosition(1, 1, 1);
    right();
    checkCaretPosition(1, 1, 2);
    right();
    checkCaretPosition(2, 2, 3);
    left();
    checkCaretPosition(1, 1, 2);
    left();
    checkCaretPosition(1, 1, 1);
    left();
    checkCaretPosition(0, 0, 0);
  }

  public void testFoldedInlay() throws Exception {
    initText("abc");
    addInlay(1);
    addCollapsedFoldRegion(1, 2, ".");
    right();
    checkCaretPosition(1, 1, 1);
    right();
    checkCaretPosition(2, 2, 2);
  }

  public void testCaretMovementWithSelection() throws Exception {
    initText("ab");
    addInlay(1);
    rightWithSelection();
    checkCaretPositionAndSelection(1, 1, 1, 0, 1);
    rightWithSelection();
    checkCaretPositionAndSelection(2, 2, 3, 0, 2);
    leftWithSelection();
    checkCaretPositionAndSelection(1, 1, 1, 0, 1);
    leftWithSelection();
    checkCaretPositionAndSelection(0, 0, 0, 0, 0);

    myEditor.getCaretModel().moveToOffset(2);
    leftWithSelection();
    checkCaretPositionAndSelection(1, 1, 2, 1, 2);
    leftWithSelection();
    checkCaretPositionAndSelection(0, 0, 0, 0, 2);
    rightWithSelection();
    checkCaretPositionAndSelection(1, 1, 2, 1, 2);
    rightWithSelection();
    checkCaretPositionAndSelection(2, 2, 3, 2, 2);
  }

  public void testBackspace() throws Exception {
    initText("ab<caret>c");
    addInlay(1);
    backspace();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 2);
    backspace();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 1);
    backspace();
    checkResultByText("c");
    checkCaretPosition(0, 0, 0);
  }

  public void testDelete() throws Exception {
    initText("a<caret>bc");
    addInlay(2);
    delete();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 1);
    delete();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 2);
    delete();
    checkResultByText("a");
    checkCaretPosition(1, 1, 2);
  }

  public void testMulticaretDelete() throws Exception {
    initText("<caret>ab <caret>ab");
    addInlay(1);
    addInlay(4);
    right();
    delete();
    checkResultByText("a<caret>b a<caret>b");
    delete();
    checkResultByText("a<caret> a<caret>");
  }

  public void testMulticaretTyping() throws Exception {
    initText("<caret>ab <caret>ab");
    addInlay(1);
    addInlay(4);
    right();
    type(' ');
    checkResultByText("a <caret>b a <caret>b");
    assertEquals(Arrays.asList(new VisualPosition(0, 2), new VisualPosition(0, 7)),
                 ContainerUtil.map(myEditor.getCaretModel().getAllCarets(), Caret::getVisualPosition));
    right();
    type(' ');
    checkResultByText("a  <caret>b a  <caret>b");
    assertEquals(Arrays.asList(new VisualPosition(0, 4), new VisualPosition(0, 10)),
                 ContainerUtil.map(myEditor.getCaretModel().getAllCarets(), Caret::getVisualPosition));
  }

  public void testDocumentEditingWithSoftWraps() throws Exception {
    initText("long line");
    configureSoftWraps(7);
    Inlay inlay = addInlay(1);
    assertNotNull(myEditor.getSoftWrapModel().getSoftWrap(5));
    new WriteCommandAction.Simple<Void>(ourProject) {
      @Override
      protected void run() throws Throwable {
        myEditor.getDocument().setText(" ");
      }
    }.execute();
    assertFalse(inlay.isValid());
  }

  private static void checkCaretPositionAndSelection(int offset, int logicalColumn, int visualColumn,
                                                     int selectionStartOffset, int selectionEndOffset) {
    checkCaretPosition(offset, logicalColumn, visualColumn);
    assertEquals(selectionStartOffset, myEditor.getSelectionModel().getSelectionStart());
    assertEquals(selectionEndOffset, myEditor.getSelectionModel().getSelectionEnd());
  }

  private static void checkCaretPosition(int offset, int logicalColumn, int visualColumn) {
    assertEquals(offset, myEditor.getCaretModel().getOffset());
    assertEquals(0, myEditor.getCaretModel().getLogicalPosition().line);
    assertEquals(logicalColumn, myEditor.getCaretModel().getLogicalPosition().column);
    assertEquals(0, myEditor.getCaretModel().getVisualPosition().line);
    assertEquals(visualColumn, myEditor.getCaretModel().getVisualPosition().column);
  }

  private static Inlay addInlay(int offset) {
    return EditorTestUtil.addInlay(myEditor, offset);
  }
}
