// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    getEditor().getCaretModel().moveToOffset(2);
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
                 ContainerUtil.map(getEditor().getCaretModel().getAllCarets(), Caret::getVisualPosition));
    right();
    type(' ');
    checkResultByText("a  <caret>b a  <caret>b");
    assertEquals(Arrays.asList(new VisualPosition(0, 4), new VisualPosition(0, 10)),
                 ContainerUtil.map(getEditor().getCaretModel().getAllCarets(), Caret::getVisualPosition));
  }

  public void testDocumentEditingWithSoftWraps() {
    initText("long line");
    configureSoftWraps(7);
    Inlay<?> inlay = addInlay(1);
    assertNotNull(getEditor().getSoftWrapModel().getSoftWrap(5));
    runWriteCommand(() -> getEditor().getDocument().setText(" "));
    assertFalse(inlay.isValid());
  }

  public void testInlayDoesntGetInsideSurrogatePair() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> getEditor().getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    Inlay<?> inlay = addInlay(2);
    assertNotNull(inlay);
    assertTrue(inlay.isValid());
    runWriteCommand(() -> ((DocumentEx)getEditor().getDocument()).moveText(2, 3, 1));
    assertFalse(inlay.isValid() && DocumentUtil.isInsideSurrogatePair(getEditor().getDocument(), inlay.getOffset()));
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
    Inlay<?> inlay1 = addInlay(1);
    Inlay<?> inlay2 = addInlay(1);
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
    assertTrue(getEditor().getInlayModel().hasInlineElementAt(new VisualPosition(0, 2)));
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

    getEditor().getCaretModel().moveToOffset(2);
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
    Inlay<?> inlay = addInlay(1);
    addInlay(1);
    right();
    right();
    Disposer.dispose(inlay);
    checkCaretPosition(1, 1, 1);
  }

  public void testCaretPositionAfterInlayDisposalToTheRight() {
    initText("ab");
    addInlay(1);
    Inlay<?> inlay = addInlay(1);
    right();
    right();
    Disposer.dispose(inlay);
    checkCaretPosition(1, 1, 2);
  }

  public void testPositionConversionForAdjacentInlays() {
    initText("ab");
    addInlay(1);
    addInlay(1);
    LogicalPosition lp = getEditor().visualToLogicalPosition(new VisualPosition(0, 2));
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
    Inlay<?> i1 = addInlay(1, false);
    Inlay<?> i2 = addInlay(2, true);
    runWriteCommand(() -> {
      getEditor().getDocument().insertString(2, " ");
      getEditor().getDocument().insertString(1, " ");
    });
    assertTrue(i1.isValid() && i1.getOffset() == 1);
    assertTrue(i2.isValid() && i2.getOffset() == 4);
  }

  public void testNoOpReplaceDoesntMoveCaret() {
    initText("<caret>abc");
    addInlay(2);
    right();
    right();
    runWriteCommand(() -> getEditor().getDocument().replaceString(1, 2, "b"));
    checkCaretPosition(2, 2, 2);
  }

  public void testCaretMovingToInlayOffset() {
    initText("<caret>abc");
    addInlay(2);
    getEditor().getCaretModel().moveToOffset(2);
    checkCaretPosition(2, 2, 3);
  }

  public void testInlayOrderAfterMerge() {
    initText("ab");
    Inlay<?> i0 = addInlay(0);
    Inlay<?> i1 = addInlay(1);
    Inlay<?> i2 = addInlay(2);
    runWriteCommand(() -> {
      getEditor().getDocument().deleteString(0, 1);
      getEditor().getDocument().deleteString(0, 1);
    });
    assertEquals(Arrays.asList(i0, i1, i2), getEditor().getInlayModel().getInlineElementsInRange(0, 0));
  }

  public void testInlayOrderAfterDocumentModification() {
    initText("abc");
    Inlay<?> i1 = addInlay(2);
    runWriteCommand(() -> getEditor().getDocument().deleteString(1, 2));
    Inlay<?> i2 = addInlay(1);
    assertEquals(Arrays.asList(i1, i2), getEditor().getInlayModel().getInlineElementsInRange(1, 1));
  }

  /**
   * Utility function for line-height block inlays testing.
   *
   * Converts geometric position of editor line that accounts block inlays to visual position that is applicable to text lines only.
   *
   * Consider the following example:
   *
   * abc <- geometric line 0, visual line 0
   * <block inlay with height being equal to line height> <- geometric line 1, visual line NA
   * cde <- geometric line 2, visual line 1
   *
   */
  private int geometricLineToVisualLine(int geometricLine) {
    return getEditor().yToVisualLine(geometricLineY(geometricLine));
  }

  private static int geometricLineY(int geometricLine) {
    return (int)(FontPreferences.DEFAULT_LINE_SPACING * TEST_LINE_HEIGHT) * geometricLine;
  }

  public void testYToVisualLineCalculationForBlockInlay() {
    initText("abc\ndef");
    addBlockInlay(1);
    assertEquals(0, geometricLineToVisualLine(0));
    assertEquals(1, geometricLineToVisualLine(2));
  }

  public void testYToVisualLineCalculationForAboveBlockInlay() {
    initText("abc\ndef");
    addBlockInlay(1, true);
    assertEquals(0, geometricLineToVisualLine(1));
    assertEquals(1, geometricLineToVisualLine(2));
  }

  public void testYToVisualLineCalculationForBlockInlayAnotherCase() {
    initText("abc\ndef\nghi");
    addBlockInlay(1);
    assertEquals(0, geometricLineToVisualLine(0));
    assertEquals(1, geometricLineToVisualLine(2));
    assertEquals(2, geometricLineToVisualLine(3));
  }

  public void testInlayInStartOfCollapsedLine() {
    initText("abc\ndef\nghi");
    addCollapsedFoldRegion(4, 8, "");
    addBlockInlay(4, true, false);
    assertEquals(0, geometricLineToVisualLine(0));
    assertEquals(1, geometricLineToVisualLine(2));
  }

  public void testInlayInStartOfCollapsedLineRelatesToPreceding() {
    initText("abc\ndef\nghi");
    addCollapsedFoldRegion(4, 8, "");
    addBlockInlay(4, true, true);
    assertEquals(geometricLineY(0), getEditor().visualLineToY(0));
    assertEquals(geometricLineY(1), getEditor().visualLineToY(1));
  }

  public void testInlayInCollapsedLine() {
    initText("abc\ndef\nghi");
    addCollapsedFoldRegion(4, 8, "");
    addBlockInlay(5, true, false);
    assertEquals(geometricLineY(0), getEditor().visualLineToY(0));
    assertEquals(geometricLineY(1), getEditor().visualLineToY(1));
  }

  public void testInlayAboveFolding() {
    initText("abc\ndef\nghi");
    addCollapsedFoldRegion(4, 8, "");
    EditorTestUtil.addBlockInlay(getEditor(), 5, true, true, true, 0, null);
    assertEquals(geometricLineY(0), getEditor().offsetToXY(0).y);
    assertEquals(geometricLineY(2), getEditor().offsetToXY(8).y);
  }

  public void testInlayIsAddedIntoCollapsedFoldRegion() {
    initText("abc");
    addCollapsedFoldRegion(0, 3, "...");
    Inlay inlay = EditorTestUtil.addBlockInlay(getEditor(), 0, true, false, 0, null);
    assertEquals((int)(FontPreferences.DEFAULT_LINE_SPACING * TEST_LINE_HEIGHT), getEditor().visualLineToY(1));
    assertNull(inlay.getBounds());
  }

  public void testInlayIsAddedIntoCollapsedFoldRegionDifferentRelatesFlag() {
    initText("abc");
    addCollapsedFoldRegion(0, 3, "...");
    Inlay inlay = EditorTestUtil.addBlockInlay(getEditor(), 0, false, false, 0, null);
    assertEquals((int)(FontPreferences.DEFAULT_LINE_SPACING * TEST_LINE_HEIGHT) * 2, getEditor().visualLineToY(1));
    assertNotNull(inlay.getBounds());
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

  public void testBlockInlayImpactsEditorWidth() {
    initText("");
    getEditor().getSettings().setAdditionalColumnsCount(0);
    getEditor().getInlayModel().addBlockElement(0, false, false, 0, new EditorCustomElementRenderer() {
      @Override
      public int calcWidthInPixels(@NotNull Inlay inlay) { return 123;}
    });
    assertEquals(123, getEditor().getContentComponent().getPreferredSize().width);
  }

  public void testOrderForAboveInlaysWithSamePriority() {
    initText("text");
    addBlockInlay(0, true);
    addBlockInlay(0, true);
    List<Inlay<?>> list1 = getEditor().getInlayModel().getBlockElementsInRange(0, 0);
    List<Inlay<?>> list2 = getEditor().getInlayModel().getBlockElementsForVisualLine(0, true);
    Collections.reverse(list2);
    assertEquals(list1, list2);
  }

  public void testOrderForBelowInlaysWithSamePriority() {
    initText("text");
    addBlockInlay(0, false);
    addBlockInlay(0, false);
    List<Inlay<?>> list1 = getEditor().getInlayModel().getBlockElementsInRange(0, 0);
    List<Inlay<?>> list2 = getEditor().getInlayModel().getBlockElementsForVisualLine(0, false);
    assertEquals(list1, list2);
  }

  public void testCorrectSoftWrappingAfterTextMovementWithInlays() {
    initText(" \tabcd efghijklmno");
    addInlay(1, TEST_CHAR_WIDTH);
    configureSoftWraps(11, false);
    verifySoftWrapPositions(7);
    WriteCommandAction.writeCommandAction(getProject()).run(() -> ((EditorEx)getEditor()).getDocument().moveText(0, 1, 7));
    verifySoftWrapPositions(7, 16);
  }

  public void testInlineElementAtDocumentEnd() {
    initText("");
    addInlay(0, 10);
    assertNull(getEditor().getInlayModel().getElementAt(new Point(5, getEditor().getLineHeight() * 3 / 2)));
  }

  public void testAfterLineEndElementAtDocumentEnd() {
    initText("");
    addAfterLineEndInlay(0, 10);
    assertNull(getEditor().getInlayModel().getElementAt(new Point(TEST_CHAR_WIDTH + 5, getEditor().getLineHeight() * 3 / 2)));
  }

  public void testInlayForDisposedEditor() {
    Editor editor = EditorFactory.getInstance().createEditor(new DocumentImpl(""));
    Inlay<?> inlay = EditorTestUtil.addInlay(editor, 0);
    assertTrue(inlay.isValid());
    EditorFactory.getInstance().releaseEditor(editor);
    assertFalse(inlay.isValid());
  }

  private void checkCaretPositionAndSelection(int offset, int logicalColumn, int visualColumn,
                                              int selectionStartOffset, int selectionEndOffset) {
    checkCaretPosition(offset, logicalColumn, visualColumn);
    assertEquals(selectionStartOffset, getEditor().getSelectionModel().getSelectionStart());
    assertEquals(selectionEndOffset, getEditor().getSelectionModel().getSelectionEnd());
  }

  private void checkCaretPosition(int offset, int logicalColumn, int visualColumn) {
    assertEquals(offset, getEditor().getCaretModel().getOffset());
    assertEquals(0, getEditor().getCaretModel().getLogicalPosition().line);
    assertEquals(logicalColumn, getEditor().getCaretModel().getLogicalPosition().column);
    assertEquals(0, getEditor().getCaretModel().getVisualPosition().line);
    assertEquals(visualColumn, getEditor().getCaretModel().getVisualPosition().column);
  }

  private void assertInlaysPositions(int... offsets) {
    assertArrayEquals(offsets,
                      getEditor().getInlayModel().getInlineElementsInRange(0, getEditor().getDocument().getTextLength()).stream()
                        .mapToInt(inlay -> inlay.getOffset()).toArray());
  }
}
