// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.editor.impl.EditorModelFixture.HIGH_SURROGATE;
import static com.intellij.openapi.editor.impl.EditorModelFixture.LOW_SURROGATE;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_CHAR_WIDTH;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_LINE_HEIGHT;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorInlayTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testCaretMovement() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      myFixture.right();
      checkCaretPosition(1, 1, 1);
      myFixture.right();
      checkCaretPosition(1, 1, 2);
      myFixture.right();
      checkCaretPosition(2, 2, 3);
      myFixture.left();
      checkCaretPosition(1, 1, 2);
      myFixture.left();
      checkCaretPosition(1, 1, 1);
      myFixture.left();
      checkCaretPosition(0, 0, 0);
    });
  }

  @Test
  public void testFoldedInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.addInlay(1);
      myFixture.addCollapsedFoldRegion(1, 2, ".");
      myFixture.right();
      checkCaretPosition(1, 1, 1);
      myFixture.right();
      checkCaretPosition(2, 2, 2);
    });
  }

  @Test
  public void testCaretMovementWithSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(1, 1, 1, 0, 1);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(2, 2, 3, 0, 2);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(1, 1, 1, 0, 1);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(0, 0, 0, 0, 0);

      myFixture.getEditor().getCaretModel().moveToOffset(2);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(1, 1, 2, 1, 2);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(0, 0, 0, 0, 2);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(1, 1, 2, 1, 2);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(2, 2, 3, 2, 2);
    });
  }

  @Test
  public void testBackspace() {
    myFixture.runTest(() -> {
      myFixture.initText("ab<caret>c");
      myFixture.addInlay(1);
      myFixture.backspace();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 2);
      myFixture.backspace();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 1);
      myFixture.backspace();
      myFixture.checkResultByText("c");
      checkCaretPosition(0, 0, 0);
    });
  }

  @Test
  public void testDelete() {
    myFixture.runTest(() -> {
      myFixture.initText("a<caret>bc");
      myFixture.addInlay(2);
      myFixture.delete();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 1);
      myFixture.delete();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 2);
      myFixture.delete();
      myFixture.checkResultByText("a");
      checkCaretPosition(1, 1, 2);
    });
  }

  @Test
  public void testMulticaretDelete() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>ab <caret>ab");
      myFixture.addInlay(1);
      myFixture.addInlay(4);
      myFixture.right();
      myFixture.delete();
      myFixture.checkResultByText("a<caret>b a<caret>b");
      myFixture.delete();
      myFixture.checkResultByText("a<caret> a<caret>");
    });
  }

  @Test
  public void testMulticaretTyping() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>ab <caret>ab");
      myFixture.addInlay(1);
      myFixture.addInlay(4);
      myFixture.right();
      myFixture.type(' ');
      myFixture.checkResultByText("a <caret>b a <caret>b");
      assertEquals(Arrays.asList(new VisualPosition(0, 2), new VisualPosition(0, 7)),
                   ContainerUtil.map(myFixture.getEditor().getCaretModel().getAllCarets(), Caret::getVisualPosition));
      myFixture.right();
      myFixture.type(' ');
      myFixture.checkResultByText("a  <caret>b a  <caret>b");
      assertEquals(Arrays.asList(new VisualPosition(0, 4), new VisualPosition(0, 10)),
                   ContainerUtil.map(myFixture.getEditor().getCaretModel().getAllCarets(), Caret::getVisualPosition));
    });
  }

  @Test
  public void testDocumentEditingWithSoftWraps() {
    myFixture.runTest(() -> {
      myFixture.initText("long line");
      myFixture.configureSoftWraps(7);
      Inlay<?> inlay = myFixture.addInlay(1);
      assertNotNull(myFixture.getEditor().getSoftWrapModel().getSoftWrap(5));
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().setText(" "));
      assertFalse(inlay.isValid());
    });
  }

  @Test
  public void testInlayDoesntGetInsideSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText(""); // Cannot set up text with singular surrogate characters directly
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
      Inlay<?> inlay = myFixture.addInlay(2);
      assertNotNull(inlay);
      assertTrue(inlay.isValid());
      myFixture.runWriteCommand(() -> ((DocumentEx)myFixture.getEditor().getDocument()).moveText(2, 3, 1));
      assertFalse(inlay.isValid() && DocumentUtil.isInsideSurrogatePair(myFixture.getEditor().getDocument(), inlay.getOffset()));
    });
  }

  @Test
  public void testTwoInlaysAtSameOffset() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      myFixture.addInlay(1);
      myFixture.right();
      checkCaretPosition(1, 1, 1);
      myFixture.right();
      checkCaretPosition(1, 1, 2);
      myFixture.right();
      checkCaretPosition(1, 1, 3);
      myFixture.right();
      checkCaretPosition(2, 2, 4);
      myFixture.left();
      checkCaretPosition(1, 1, 3);
      myFixture.left();
      checkCaretPosition(1, 1, 2);
      myFixture.left();
      checkCaretPosition(1, 1, 1);
      myFixture.left();
      checkCaretPosition(0, 0, 0);
    });
  }

  @Test
  public void testTypingBetweenInlaysAtSameOffset() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      Inlay<?> inlay1 = myFixture.addInlay(1);
      Inlay<?> inlay2 = myFixture.addInlay(1);
      myFixture.right();
      myFixture.right();
      myFixture.type(' ');
      myFixture.checkResultByText("a <caret>b");
      checkCaretPosition(2, 2, 3);
      assertTrue(inlay1.isValid());
      assertEquals(1, inlay1.getOffset());
      assertTrue(inlay2.isValid());
      assertEquals(2, inlay2.getOffset());
    });
  }

  @Test
  public void testHasInlayAtVisualPosition() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      myFixture.addInlay(1);
      assertTrue(myFixture.getEditor().getInlayModel().hasInlineElementAt(new VisualPosition(0, 2)));
    });
  }

  @Test
  public void testBackspaceWithTwoInlaysAtSameOffset() {
    myFixture.runTest(() -> {
      myFixture.initText("ab<caret>c");
      myFixture.addInlay(1);
      myFixture.addInlay(1);
      myFixture.backspace();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 3);
      myFixture.backspace();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 2);
      myFixture.backspace();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 1);
      myFixture.backspace();
      myFixture.checkResultByText("c");
      checkCaretPosition(0, 0, 0);
    });
  }

  @Test
  public void testDeleteWithTwoInlaysAtSameOffset() {
    myFixture.runTest(() -> {
      myFixture.initText("a<caret>bc");
      myFixture.addInlay(2);
      myFixture.addInlay(2);
      myFixture.delete();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 1);
      myFixture.delete();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 2);
      myFixture.delete();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 3);
      myFixture.delete();
      myFixture.checkResultByText("a");
      checkCaretPosition(1, 1, 3);
    });
  }

  @Test
  public void testSelectionWithTwoInlaysAtSameOffset() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      myFixture.addInlay(1);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(1, 1, 1, 0, 1);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(2, 2, 4, 0, 2);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(1, 1, 1, 0, 1);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(0, 0, 0, 0, 0);

      myFixture.getEditor().getCaretModel().moveToOffset(2);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(1, 1, 3, 1, 2);
      myFixture.leftWithSelection();
      checkCaretPositionAndSelection(0, 0, 0, 0, 2);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(1, 1, 3, 1, 2);
      myFixture.rightWithSelection();
      checkCaretPositionAndSelection(2, 2, 4, 2, 2);
    });
  }

  @Test
  public void testDeleteBetweenInlays() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.addInlay(1);
      myFixture.addInlay(2);
      myFixture.right();
      myFixture.right();
      myFixture.delete();
      myFixture.checkResultByText("ac");
      checkCaretPosition(1, 1, 2);
      assertInlaysPositions(1, 1);
    });
  }

  @Test
  public void testCaretPositionAfterInlayDisposalToTheLeft() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      Inlay<?> inlay = myFixture.addInlay(1);
      myFixture.addInlay(1);
      myFixture.right();
      myFixture.right();
      Disposer.dispose(inlay);
      checkCaretPosition(1, 1, 1);
    });
  }

  @Test
  public void testCaretPositionAfterInlayDisposalToTheRight() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      Inlay<?> inlay = myFixture.addInlay(1);
      myFixture.right();
      myFixture.right();
      Disposer.dispose(inlay);
      checkCaretPosition(1, 1, 2);
    });
  }

  @Test
  public void testPositionConversionForAdjacentInlays() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.addInlay(1);
      myFixture.addInlay(1);
      LogicalPosition lp = myFixture.getEditor().visualToLogicalPosition(new VisualPosition(0, 2));
      assertEquals(new LogicalPosition(0, 1), lp);
      assertFalse(lp.leansForward);
    });
  }

  @Test
  public void testFoldingOperationDoesntMoveCaretFromBetweenInlays() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.addInlay(2);
      myFixture.addInlay(2);
      myFixture.right();
      myFixture.right();
      myFixture.right();
      myFixture.addCollapsedFoldRegion(0, 1, "...");
      checkCaretPosition(2, 2, 5);
    });
  }

  @Test
  public void testBehaviourOnTextInsertion() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      Inlay<?> i1 = myFixture.addInlay(1, false);
      Inlay<?> i2 = myFixture.addInlay(2, true);
      myFixture.runWriteCommand(() -> {
        myFixture.getEditor().getDocument().insertString(2, " ");
        myFixture.getEditor().getDocument().insertString(1, " ");
      });
      assertTrue(i1.isValid() && i1.getOffset() == 1);
      assertTrue(i2.isValid() && i2.getOffset() == 4);
    });
  }

  @Test
  public void testNoOpReplaceDoesntMoveCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>abc");
      myFixture.addInlay(2);
      myFixture.right();
      myFixture.right();
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().replaceString(1, 2, "b"));
      checkCaretPosition(2, 2, 2);
    });
  }

  @Test
  public void testCaretMovingToInlayOffset() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>abc");
      myFixture.addInlay(2);
      myFixture.getEditor().getCaretModel().moveToOffset(2);
      checkCaretPosition(2, 2, 3);
    });
  }

  @Test
  public void testInlayOrderAfterMerge() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      Inlay<?> i0 = myFixture.addInlay(0);
      Inlay<?> i1 = myFixture.addInlay(1);
      Inlay<?> i2 = myFixture.addInlay(2);
      myFixture.runWriteCommand(() -> {
        myFixture.getEditor().getDocument().deleteString(0, 1);
        myFixture.getEditor().getDocument().deleteString(0, 1);
      });
      assertEquals(Arrays.asList(i0, i1, i2), myFixture.getEditor().getInlayModel().getInlineElementsInRange(0, 0));
    });
  }

  @Test
  public void testInlayOrderAfterDocumentModification() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      Inlay<?> i1 = myFixture.addInlay(2);
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(1, 2));
      Inlay<?> i2 = myFixture.addInlay(1);
      assertEquals(Arrays.asList(i1, i2), myFixture.getEditor().getInlayModel().getInlineElementsInRange(1, 1));
    });
  }

  /**
   * Utility function for line-height block inlays testing.
   * <p>
   * Converts geometric position of editor line that accounts block inlays to visual position that is applicable to text lines only.
   * <p>
   * Consider the following example:
   * <p>
   * abc <- geometric line 0, visual line 0
   * <block inlay with height being equal to line height> <- geometric line 1, visual line NA
   * cde <- geometric line 2, visual line 1
   *
   */
  private int geometricLineToVisualLine(int geometricLine) {
    return myFixture.getEditor().yToVisualLine(geometricLineY(geometricLine));
  }

  private static int geometricLineY(int geometricLine) {
    return (int)(FontPreferences.DEFAULT_LINE_SPACING * TEST_LINE_HEIGHT) * geometricLine;
  }

  @Test
  public void testYToVisualLineCalculationForBlockInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef");
      myFixture.addBlockInlay(1);
      assertEquals(0, geometricLineToVisualLine(0));
      assertEquals(1, geometricLineToVisualLine(2));
    });
  }

  @Test
  public void testYToVisualLineCalculationForAboveBlockInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef");
      myFixture.addBlockInlay(1, true);
      assertEquals(0, geometricLineToVisualLine(1));
      assertEquals(1, geometricLineToVisualLine(2));
    });
  }

  @Test
  public void testYToVisualLineCalculationForBlockInlayAnotherCase() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi");
      myFixture.addBlockInlay(1);
      assertEquals(0, geometricLineToVisualLine(0));
      assertEquals(1, geometricLineToVisualLine(2));
      assertEquals(2, geometricLineToVisualLine(3));
    });
  }

  @Test
  public void testInlayInStartOfCollapsedLine() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi");
      myFixture.addCollapsedFoldRegion(4, 8, "");
      myFixture.addBlockInlay(4, true, false);
      assertEquals(0, geometricLineToVisualLine(0));
      assertEquals(1, geometricLineToVisualLine(2));
    });
  }

  @Test
  public void testInlayInStartOfCollapsedLineRelatesToPreceding() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi");
      myFixture.addCollapsedFoldRegion(4, 8, "");
      myFixture.addBlockInlay(4, true, true);
      assertEquals(geometricLineY(0), myFixture.getEditor().visualLineToY(0));
      assertEquals(geometricLineY(1), myFixture.getEditor().visualLineToY(1));
    });
  }

  @Test
  public void testInlayInCollapsedLine() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi");
      myFixture.addCollapsedFoldRegion(4, 8, "");
      myFixture.addBlockInlay(5, true, false);
      assertEquals(geometricLineY(0), myFixture.getEditor().visualLineToY(0));
      assertEquals(geometricLineY(1), myFixture.getEditor().visualLineToY(1));
    });
  }

  @Test
  public void testInlayAboveFolding() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi");
      myFixture.addCollapsedFoldRegion(4, 8, "");
      EditorTestUtil.addBlockInlay(myFixture.getEditor(), 5, true, true, true, 0, null);
      assertEquals(geometricLineY(0), myFixture.getEditor().offsetToXY(0).y);
      assertEquals(geometricLineY(2), myFixture.getEditor().offsetToXY(8).y);
    });
  }

  @Test
  public void testInlayIsAddedIntoCollapsedFoldRegion() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.addCollapsedFoldRegion(0, 3, "...");
      Inlay<?> inlay = EditorTestUtil.addBlockInlay(myFixture.getEditor(), 0, true, false, 0, null);
      assertEquals((int)(FontPreferences.DEFAULT_LINE_SPACING * TEST_LINE_HEIGHT), myFixture.getEditor().visualLineToY(1));
      assertNull(inlay.getBounds());
    });
  }

  @Test
  public void testInlayIsAddedIntoCollapsedFoldRegionDifferentRelatesFlag() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.addCollapsedFoldRegion(0, 3, "...");
      Inlay<?> inlay = EditorTestUtil.addBlockInlay(myFixture.getEditor(), 0, false, false, 0, null);
      assertEquals((int)(FontPreferences.DEFAULT_LINE_SPACING * TEST_LINE_HEIGHT) * 2, myFixture.getEditor().visualLineToY(1));
      assertNotNull(inlay.getBounds());
    });
  }

  @Test
  public void testVerticalCaretMovementInPresenceOfBothTypesOfInlays() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\nd<caret>ef\nghi");
      myFixture.addBlockInlay(0);
      myFixture.addInlay(4, TEST_CHAR_WIDTH * 2);
      myFixture.down();
      myFixture.checkResultByText("abc\ndef\nghi<caret>");
    });
  }

  @Test
  public void testInlayAtLineEndCausesSoftWrapping() {
    myFixture.runTest(() -> {
      myFixture.initText("abcd efgh");
      myFixture.addInlay(9, TEST_CHAR_WIDTH * 3);
      myFixture.configureSoftWraps(10);
      myFixture.verifySoftWrapPositions(5);
    });
  }

  @Test
  public void testInlayWithDisabledSoftWrappingDoesNotCauseSoftWrapping() {
    myFixture.runTest(() -> {
      myFixture.initText("abcd efgh");
      EditorCustomElementRenderer renderer = new EditorCustomElementRenderer() {
        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) {
          return TEST_CHAR_WIDTH * 3;
        }
      };
      myFixture.getEditor().getInlayModel().addInlineElement(9, new InlayProperties().disableSoftWrapping(true), renderer);
      myFixture.configureSoftWraps(10);
      myFixture.verifySoftWrapPositions();
    });
  }

  @Test
  public void testBlockInlayImpactsEditorWidth() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.getEditor().getInlayModel().addBlockElement(0, false, false, 0, new EditorCustomElementRenderer() {
        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) { return 123; }
      });
      assertEquals(123, myFixture.getEditor().getContentComponent().getPreferredSize().width);
    });
  }

  @Test
  public void testOrderForAboveInlaysWithSamePriority() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(0, true);
      myFixture.addBlockInlay(0, true);
      List<Inlay<?>> list1 = myFixture.getEditor().getInlayModel().getBlockElementsInRange(0, 0);
      List<Inlay<?>> list2 = myFixture.getEditor().getInlayModel().getBlockElementsForVisualLine(0, true);
      Collections.reverse(list2);
      assertEquals(list1, list2);
    });
  }

  @Test
  public void testOrderForBelowInlaysWithSamePriority() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(0, false);
      myFixture.addBlockInlay(0, false);
      List<Inlay<?>> list1 = myFixture.getEditor().getInlayModel().getBlockElementsInRange(0, 0);
      List<Inlay<?>> list2 = myFixture.getEditor().getInlayModel().getBlockElementsForVisualLine(0, false);
      assertEquals(list1, list2);
    });
  }

  @Test
  public void testCorrectSoftWrappingAfterTextMovementWithInlays() {
    myFixture.runTest(() -> {
      myFixture.initText(" \tabcd efghijklmno");
      myFixture.addInlay(1, TEST_CHAR_WIDTH);
      myFixture.configureSoftWraps(11, false);
      myFixture.verifySoftWrapPositions(7);
      WriteCommandAction.writeCommandAction(myFixture.getProject())
        .run(() -> ((EditorEx)myFixture.getEditor()).getDocument().moveText(0, 1, 7));
      myFixture.verifySoftWrapPositions(7, 16);
    });
  }

  @Test
  public void testInlineElementAtDocumentEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      myFixture.addInlay(0, 10);
      assertNull(myFixture.getEditor().getInlayModel().getElementAt(new Point(5, myFixture.getEditor().getLineHeight() * 3 / 2)));
    });
  }

  @Test
  public void testAfterLineEndElementAtDocumentEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      myFixture.addAfterLineEndInlay(0, 10);
      assertNull(myFixture.getEditor().getInlayModel()
                   .getElementAt(new Point(TEST_CHAR_WIDTH + 5, myFixture.getEditor().getLineHeight() * 3 / 2)));
    });
  }

  @Test
  public void testInlayForDisposedEditor() {
    myFixture.runTest(() -> {
      Editor editor = EditorFactory.getInstance().createEditor(new DocumentImpl(""));
      Inlay<?> inlay = EditorTestUtil.addInlay(editor, 0);
      assertTrue(inlay.isValid());
      EditorFactory.getInstance().releaseEditor(editor);
      assertFalse(inlay.isValid());
    });
  }

  @Test
  public void testInlayForDisposedEditor2() {
    myFixture.runTest(() -> {
      Editor editor = EditorFactory.getInstance().createEditor(new DocumentImpl(""));
      EditorFactory.getInstance().releaseEditor(editor);

      Inlay<?> inlay1 = EditorTestUtil.addInlay(editor, 0);
      Inlay<?> inlay2 = EditorTestUtil.addBlockInlay(editor, 0, false, false, 10, 10);
      Inlay<?> inlay3 = EditorTestUtil.addAfterLineEndInlay(editor, 0, 10);

      // inlays can be registered after Editor disposal, as many existing calls do not handle nullable result
      assertNotNull(inlay1);
      assertNotNull(inlay2);
      assertNotNull(inlay3);

      assertFalse(inlay1.isValid());
      assertFalse(inlay2.isValid());
      assertFalse(inlay3.isValid());

      Disposable disposable = Disposer.newDisposable();
      assertFalse(Disposer.tryRegister(inlay1, disposable));
      assertFalse(Disposer.tryRegister(inlay2, disposable));
      assertFalse(Disposer.tryRegister(inlay3, disposable));

      assertThrows(RuntimeException.class, () -> {
        Disposer.register(inlay1, disposable);
      });
      assertThrows(RuntimeException.class, () -> {
        Disposer.register(inlay2, disposable);
      });
      assertThrows(RuntimeException.class, () -> {
        Disposer.register(inlay3, disposable);
      });
    });
  }

  @Test
  public void testInlayAreDisposables1() {
    myFixture.runTest(() -> {
      CheckedDisposable disposable1 = Disposer.newCheckedDisposable();
      CheckedDisposable disposable2 = Disposer.newCheckedDisposable();
      CheckedDisposable disposable3 = Disposer.newCheckedDisposable();

      Editor editor = EditorFactory.getInstance().createEditor(new DocumentImpl(""));

      Inlay<?> inlay1 = EditorTestUtil.addInlay(editor, 0);
      Inlay<?> inlay2 = EditorTestUtil.addBlockInlay(editor, 0, false, false, 10, 10);
      Inlay<?> inlay3 = EditorTestUtil.addAfterLineEndInlay(editor, 0, 10);

      try {
        try {
          Disposer.register(inlay1, disposable1);
          Disposer.register(inlay2, disposable2);
          Disposer.register(inlay3, disposable3);
        }
        finally {
          EditorFactory.getInstance().releaseEditor(editor);
        }

        assertTrue(disposable1.isDisposed());
        assertTrue(disposable2.isDisposed());
        assertTrue(disposable3.isDisposed());
      }
      finally {
        Disposer.dispose(disposable1);
        Disposer.dispose(disposable2);
        Disposer.dispose(disposable3);
      }
    });
  }

  @Test
  public void testInlayAreDisposables2() {
    myFixture.runTest(() -> {
      CheckedDisposable disposable1 = Disposer.newCheckedDisposable();
      CheckedDisposable disposable2 = Disposer.newCheckedDisposable();
      CheckedDisposable disposable3 = Disposer.newCheckedDisposable();

      Editor editor = EditorFactory.getInstance().createEditor(new DocumentImpl("some\nmultiline\ntext"));

      Inlay<?> inlay1 = EditorTestUtil.addInlay(editor, 10);
      Inlay<?> inlay2 = EditorTestUtil.addBlockInlay(editor, 10, false, false, 10, 10);
      Inlay<?> inlay3 = EditorTestUtil.addAfterLineEndInlay(editor, 10, 10);
      try {
        Disposer.register(inlay1, disposable1);
        Disposer.register(inlay2, disposable2);
        Disposer.register(inlay3, disposable3);

        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          editor.getDocument().setText("qwerty");
        });

        assertFalse(inlay1.isValid());
        assertFalse(inlay2.isValid());
        assertFalse(inlay3.isValid());

        assertTrue(disposable1.isDisposed());
        assertTrue(disposable2.isDisposed());
        assertTrue(disposable3.isDisposed());
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);

        Disposer.dispose(disposable1);
        Disposer.dispose(disposable2);
        Disposer.dispose(disposable3);
      }
    });
  }

  private void checkCaretPositionAndSelection(int offset, int logicalColumn, int visualColumn,
                                              int selectionStartOffset, int selectionEndOffset) {
    checkCaretPosition(offset, logicalColumn, visualColumn);
    assertEquals(selectionStartOffset, myFixture.getEditor().getSelectionModel().getSelectionStart());
    assertEquals(selectionEndOffset, myFixture.getEditor().getSelectionModel().getSelectionEnd());
  }

  private void checkCaretPosition(int offset, int logicalColumn, int visualColumn) {
    assertEquals(offset, myFixture.getEditor().getCaretModel().getOffset());
    assertEquals(0, myFixture.getEditor().getCaretModel().getLogicalPosition().line);
    assertEquals(logicalColumn, myFixture.getEditor().getCaretModel().getLogicalPosition().column);
    assertEquals(0, myFixture.getEditor().getCaretModel().getVisualPosition().line);
    assertEquals(visualColumn, myFixture.getEditor().getCaretModel().getVisualPosition().column);
  }

  private void assertInlaysPositions(int... offsets) {
    Editor editor = myFixture.getEditor();
    assertArrayEquals(offsets,
                      editor.getInlayModel().getInlineElementsInRange(0, editor.getDocument().getTextLength()).stream()
                        .mapToInt(inlay -> inlay.getOffset()).toArray());
  }
}
