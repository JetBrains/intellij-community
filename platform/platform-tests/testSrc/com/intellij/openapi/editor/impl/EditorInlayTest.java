/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;

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

  public void testInlayDoesntGetInsideSurrogatePair() throws Exception {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> myEditor.getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    Inlay inlay = addInlay(2);
    assertNotNull(inlay);
    assertTrue(inlay.isValid());
    runWriteCommand(() -> ((DocumentEx)myEditor.getDocument()).moveText(2, 3, 1));
    assertFalse(inlay.isValid() && DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), inlay.getOffset()));
  }

  public void testTwoInlaysAtSameOffset() throws Exception {
    initText("ab");
    addInlay(1);
    addInlay(1);
    right();
    checkCaretPosition(1, 1, 1);
    right();
    checkCaretPosition(1, 1, 2);
    right();
    checkCaretPosition(1, 1, 3);
    right();
    checkCaretPosition(2, 2, 4);
    left();
    checkCaretPosition(1, 1, 3);
    left();
    checkCaretPosition(1, 1, 2);
    left();
    checkCaretPosition(1, 1, 1);
    left();
    checkCaretPosition(0, 0, 0);
  }

  public void testTypingBetweenInlaysAtSameOffset() throws Exception {
    initText("ab");
    Inlay inlay1 = addInlay(1);
    Inlay inlay2 = addInlay(1);
    right();
    right();
    type(' ');
    checkResultByText("a <caret>b");
    checkCaretPosition(2, 2, 3);
    assertTrue(inlay1.isValid());
    assertEquals(1, inlay1.getOffset());
    assertTrue(inlay2.isValid());
    assertEquals(2, inlay2.getOffset());
  }

  public void testHasInlayAtVisualPosition() throws Exception {
    initText("ab");
    addInlay(1);
    addInlay(1);
    assertTrue(myEditor.getInlayModel().hasInlineElementAt(new VisualPosition(0, 2)));
  }

  public void testBackspaceWithTwoInlaysAtSameOffset() throws Exception {
    initText("ab<caret>c");
    addInlay(1);
    addInlay(1);
    backspace();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 3);
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

  public void testDeleteWithTwoInlaysAtSameOffset() throws Exception {
    initText("a<caret>bc");
    addInlay(2);
    addInlay(2);
    delete();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 1);
    delete();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 2);
    delete();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 3);
    delete();
    checkResultByText("a");
    checkCaretPosition(1, 1, 3);
  }

  public void testSelectionWithTwoInlaysAtSameOffset() throws Exception {
    initText("ab");
    addInlay(1);
    addInlay(1);
    rightWithSelection();
    checkCaretPositionAndSelection(1, 1, 1, 0, 1);
    rightWithSelection();
    checkCaretPositionAndSelection(2, 2, 4, 0, 2);
    leftWithSelection();
    checkCaretPositionAndSelection(1, 1, 1, 0, 1);
    leftWithSelection();
    checkCaretPositionAndSelection(0, 0, 0, 0, 0);

    myEditor.getCaretModel().moveToOffset(2);
    leftWithSelection();
    checkCaretPositionAndSelection(1, 1, 3, 1, 2);
    leftWithSelection();
    checkCaretPositionAndSelection(0, 0, 0, 0, 2);
    rightWithSelection();
    checkCaretPositionAndSelection(1, 1, 3, 1, 2);
    rightWithSelection();
    checkCaretPositionAndSelection(2, 2, 4, 2, 2);
  }

  public void testDeleteBetweenInlays() throws Exception {
    initText("abc");
    addInlay(1);
    addInlay(2);
    right();
    right();
    delete();
    checkResultByText("ac");
    checkCaretPosition(1, 1, 2);
    assertInlaysPositions(1, 1);
  }

  public void testCaretPositionAfterInlayDisposalToTheLeft() throws Exception {
    initText("ab");
    Inlay inlay = addInlay(1);
    addInlay(1);
    right();
    right();
    Disposer.dispose(inlay);
    checkCaretPosition(1, 1, 1);
  }

  public void testCaretPositionAfterInlayDisposalToTheRight() throws Exception {
    initText("ab");
    addInlay(1);
    Inlay inlay = addInlay(1);
    right();
    right();
    Disposer.dispose(inlay);
    checkCaretPosition(1, 1, 2);
  }

  public void testPositionConversionForAdjacentInlays() throws Exception {
    initText("ab");
    addInlay(1);
    addInlay(1);
    LogicalPosition lp = myEditor.visualToLogicalPosition(new VisualPosition(0, 2));
    assertEquals(new LogicalPosition(0, 1), lp);
    assertFalse(lp.leansForward);
  }

  public void testFoldingOperationDoesntMoveCaretFromBetweenInlays() throws Exception {
    initText("abc");
    addInlay(2);
    addInlay(2);
    right();
    right();
    right();
    addCollapsedFoldRegion(0, 1, "...");
    checkCaretPosition(2, 2, 5);
  }

  public void testBehaviourOnTextInsertion() throws Exception {
    initText("abc");
    Inlay i1 = addInlay(1, false);
    Inlay i2 = addInlay(2, true);
    WriteCommandAction.runWriteCommandAction(ourProject, () -> {
      myEditor.getDocument().insertString(2, " ");
      myEditor.getDocument().insertString(1, " ");
    });
    assertTrue(i1.isValid() && i1.getOffset() == 1);
    assertTrue(i2.isValid() && i2.getOffset() == 4);
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

  private static void assertInlaysPositions(int... offsets) {
    assertArrayEquals(offsets, 
                      myEditor.getInlayModel().getInlineElementsInRange(0, myEditor.getDocument().getTextLength()).stream()
                        .mapToInt(inlay -> inlay.getOffset()).toArray());
  }
}
