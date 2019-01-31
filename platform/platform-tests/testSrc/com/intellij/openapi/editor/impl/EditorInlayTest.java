// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

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
  public void testCaretMovement() {
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

  public void testFoldedInlay() {
    initText("abc");
    addInlay(1);
    addCollapsedFoldRegion(1, 2, ".");
    right();
    checkCaretPosition(1, 1, 1);
    right();
    checkCaretPosition(2, 2, 2);
  }

  public void testCaretMovementWithSelection() {
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

  public void testBackspace() {
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

  public void testDelete() {
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

  public void testMulticaretDelete() {
    initText("<caret>ab <caret>ab");
    addInlay(1);
    addInlay(4);
    right();
    delete();
    checkResultByText("a<caret>b a<caret>b");
    delete();
    checkResultByText("a<caret> a<caret>");
  }

  public void testMulticaretTyping() {
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

  public void testDocumentEditingWithSoftWraps() {
    initText("long line");
    configureSoftWraps(7);
    Inlay inlay = addInlay(1);
    assertNotNull(myEditor.getSoftWrapModel().getSoftWrap(5));
    runWriteCommand(() -> myEditor.getDocument().setText(" "));
    assertFalse(inlay.isValid());
  }

  public void testInlayDoesntGetInsideSurrogatePair() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> myEditor.getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    Inlay inlay = addInlay(2);
    assertNotNull(inlay);
    assertTrue(inlay.isValid());
    runWriteCommand(() -> ((DocumentEx)myEditor.getDocument()).moveText(2, 3, 1));
    assertFalse(inlay.isValid() && DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), inlay.getOffset()));
  }

  public void testTwoInlaysAtSameOffset() {
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

  public void testTypingBetweenInlaysAtSameOffset() {
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

  public void testHasInlayAtVisualPosition() {
    initText("ab");
    addInlay(1);
    addInlay(1);
    assertTrue(myEditor.getInlayModel().hasInlineElementAt(new VisualPosition(0, 2)));
  }

  public void testBackspaceWithTwoInlaysAtSameOffset() {
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

  public void testDeleteWithTwoInlaysAtSameOffset() {
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

  public void testSelectionWithTwoInlaysAtSameOffset() {
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

  public void testDeleteBetweenInlays() {
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

  public void testCaretPositionAfterInlayDisposalToTheLeft() {
    initText("ab");
    Inlay inlay = addInlay(1);
    addInlay(1);
    right();
    right();
    Disposer.dispose(inlay);
    checkCaretPosition(1, 1, 1);
  }

  public void testCaretPositionAfterInlayDisposalToTheRight() {
    initText("ab");
    addInlay(1);
    Inlay inlay = addInlay(1);
    right();
    right();
    Disposer.dispose(inlay);
    checkCaretPosition(1, 1, 2);
  }

  public void testPositionConversionForAdjacentInlays() {
    initText("ab");
    addInlay(1);
    addInlay(1);
    LogicalPosition lp = myEditor.visualToLogicalPosition(new VisualPosition(0, 2));
    assertEquals(new LogicalPosition(0, 1), lp);
    assertFalse(lp.leansForward);
  }

  public void testFoldingOperationDoesntMoveCaretFromBetweenInlays() {
    initText("abc");
    addInlay(2);
    addInlay(2);
    right();
    right();
    right();
    addCollapsedFoldRegion(0, 1, "...");
    checkCaretPosition(2, 2, 5);
  }

  public void testBehaviourOnTextInsertion() {
    initText("abc");
    Inlay i1 = addInlay(1, false);
    Inlay i2 = addInlay(2, true);
    runWriteCommand(() -> {
      myEditor.getDocument().insertString(2, " ");
      myEditor.getDocument().insertString(1, " ");
    });
    assertTrue(i1.isValid() && i1.getOffset() == 1);
    assertTrue(i2.isValid() && i2.getOffset() == 4);
  }

  public void testNoOpReplaceDoesntMoveCaret() {
    initText("<caret>abc");
    addInlay(2);
    right();
    right();
    runWriteCommand(() -> myEditor.getDocument().replaceString(1, 2, "b"));
    checkCaretPosition(2, 2, 2);
  }

  public void testCaretMovingToInlayOffset() {
    initText("<caret>abc");
    addInlay(2);
    myEditor.getCaretModel().moveToOffset(2);
    checkCaretPosition(2, 2, 3);
  }

  public void testInlayOrderAfterMerge() {
    initText("ab");
    Inlay i0 = addInlay(0);
    Inlay i1 = addInlay(1);
    Inlay i2 = addInlay(2);
    runWriteCommand(() -> {
      myEditor.getDocument().deleteString(0, 1);
      myEditor.getDocument().deleteString(0, 1);
    });
    assertEquals(Arrays.asList(i0, i1, i2), myEditor.getInlayModel().getInlineElementsInRange(0, 0));
  }

  public void testInlayOrderAfterDocumentModification() {
    initText("abc");
    Inlay i1 = addInlay(2);
    runWriteCommand(() -> myEditor.getDocument().deleteString(1, 2));
    Inlay i2 = addInlay(1);
    assertEquals(Arrays.asList(i1, i2), myEditor.getInlayModel().getInlineElementsInRange(1, 1));
  }

  public void testYToVisualLineCalculationForBlockInlay() {
    initText("abc\ndef");
    addBlockInlay(1);
    assertEquals(1, myEditor.yToVisualLine(TEST_LINE_HEIGHT * 2));
  }

  public void testYToVisualLineCalculationForBlockInlayAnotherCase() {
    initText("abc\ndef\nghi");
    addBlockInlay(1);
    assertEquals(1, myEditor.yToVisualLine(TEST_LINE_HEIGHT * 2));
  }

  public void testInlayIsAddedIntoCollapsedFoldRegion() {
    initText("abc");
    addCollapsedFoldRegion(0, 3, "...");
    addBlockInlay(0);
    assertEquals(TEST_LINE_HEIGHT, myEditor.visualLineToY(1));
  }

  public void testVerticalCaretMovementInPresenceOfBothTypesOfInlays() {
    initText("abc\nd<caret>ef\nghi");
    addBlockInlay(0);
    addInlay(4, TEST_CHAR_WIDTH * 2);
    down();
    checkResultByText("abc\ndef\nghi<caret>");
  }

  public void testInlayAtLineEndCausesSoftWrapping() {
    initText("abcd efgh");
    addInlay(9, TEST_CHAR_WIDTH * 3);
    configureSoftWraps(10);
    verifySoftWrapPositions(5);
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
