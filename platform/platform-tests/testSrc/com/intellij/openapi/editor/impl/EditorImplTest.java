// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextHitInfo;
import java.text.AttributedString;
import java.util.Collections;

public class EditorImplTest extends AbstractEditorTest {
  public void testPositionCalculationForZeroWidthChars() {
    initText("some\u2044text");
    VisualPosition pos = new VisualPosition(0, 6);
    VisualPosition recalculatedPos = getEditor().xyToVisualPosition(getEditor().visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }

  public void testPositionCalculationOnEmptyLine() {
    initText("""
               text with

               empty line""");
    VisualPosition pos = new VisualPosition(1, 0);
    VisualPosition recalculatedPos = getEditor().xyToVisualPosition(getEditor().visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }

  public void testShiftDragAddsToSelection() {
    initText("A quick brown fox");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000); // enable drag testing
    mouse().clickAt(0, 1);
    mouse().shift().pressAt(0, 2).dragTo(0, 3).release();
    checkResultByText("A<selection> q<caret></selection>uick brown fox");
  }

  public void testSoftWrapsRecalculationInASpecificCase() {
    configureFromFileText(getTestName(false) + ".java",
                          """
                            <selection>class Foo {
                            \t@Override
                            \tpublic boolean equals(Object other) {
                            \t\treturn this == other;
                            \t}
                            }</selection>""");
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(getEditor());
    configureSoftWraps(32, false);

    // verify initial state
    assertEquals(4, EditorUtil.getTabSize(getEditor()));
    assertEquals("[FoldRegion +(59:64), placeholder=' { ', FoldRegion +(85:88), placeholder=' }']", getEditor().getFoldingModel().toString());
    verifySoftWrapPositions(52, 85);

    runWriteCommand(() -> {
      Document document = getEditor().getDocument();
      for (int i = document.getLineCount() - 1; i >= 0; i--) {
        document.insertString(document.getLineStartOffset(i), "//");
      }
    });


    verifySoftWrapPositions(58, 93);
  }

  public void testCorrectVisibleLineCountCalculation() {
    initText("""
               line containing FOLDED_REGION
               next <caret>line
               last line""");
    foldOccurrences("FOLDED_REGION", "...");
    configureSoftWraps(16); // wrap right at folded region start
    verifySoftWrapPositions(16);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    checkResultByText("""
                        line containing FOLDED_REGION
                        next line
                        last <caret>line""");
  }

  public void testInsertingFirstTab() {
    initText(" <caret>space-indented line");
    configureSoftWraps(100);
    getEditor().getSettings().setUseTabCharacter(true);

    executeAction(IdeActions.ACTION_EDITOR_TAB);
    checkResultByText(" \t<caret>space-indented line");
  }

  public void testNoExceptionDuringBulkModeDocumentUpdate() {
    initText("something");
    DocumentEx document = (DocumentEx)getEditor().getDocument();
    runWriteCommand(() -> DocumentUtil.executeInBulk(document, ()-> document.setText("something\telse")));

    checkResultByText("something\telse");
  }

  public void testCaretMergeDuringBulkModeDocumentUpdate() {
    initText("a<selection>bcdef<caret></selection>g\n" +
             "a<selection>bcdef<caret></selection>g");
    runWriteCommand(() -> {
      DocumentEx document = (DocumentEx)getEditor().getDocument();
      DocumentUtil.executeInBulk(document, ()-> {
        // delete selected text
        document.deleteString(1, 6);
        document.deleteString(4, 9);
      });
    });

    checkResultByText("a<caret>g\n" +
                      "a<caret>g");
  }

  public void testPositionCalculationForOneCharacterFolds() {
    initText("something");
    addCollapsedFoldRegion(1, 2, "...");
    addCollapsedFoldRegion(3, 4, "...");

    assertEquals(new VisualPosition(0, 5), getEditor().logicalToVisualPosition(new LogicalPosition(0, 3)));
  }
  
  public void testNavigationIntoFoldedRegionWithSoftWrapsEnabled() {
    initText("something");
    addCollapsedFoldRegion(4, 8, "...");
    configureSoftWraps(1000);
    
    getEditor().getCaretModel().moveToOffset(5);
    
    assertEquals(new VisualPosition(0, 5), getEditor().getCaretModel().getVisualPosition());
  }
  
  public void testPositionCalculationForMultilineFoldingWithEmptyPlaceholder() {
    initText("""
               line1
               line2
               line3
               line4""");
    addCollapsedFoldRegion(6, 17, "");
    configureSoftWraps(1000);
    
    assertEquals(new LogicalPosition(3, 0), getEditor().visualToLogicalPosition(new VisualPosition(2, 0)));
  }
  
  public void testNavigationInsideNonNormalizedLineTerminator() {
    initText("");
    ((DocumentImpl)getEditor().getDocument()).setAcceptSlashR(true);
    runWriteCommand(() -> getEditor().getDocument().insertString(0, "abc\r\ndef"));

    getEditor().getCaretModel().moveToOffset(4);
    
    assertEquals(new LogicalPosition(0, 3), getEditor().getCaretModel().getLogicalPosition());
    assertEquals(new VisualPosition(0, 3), getEditor().getCaretModel().getVisualPosition());
  }
  
  public void testSoftWrapModeUpdateDuringBulkModeChange() {
    initText("long long line<caret>");
    configureSoftWraps(12);
    DocumentEx document = (DocumentEx)getEditor().getDocument();
    runWriteCommand(() -> DocumentUtil.executeInBulk(document, ()-> document.replaceString(4, 5, "-")));

    assertEquals(new VisualPosition(1, 5), getEditor().getCaretModel().getVisualPosition());
  }
  
  public void testSuccessiveBulkModeOperations() {
    initText("some text");
    DocumentEx document = (DocumentEx)getEditor().getDocument();

    runWriteCommand(() -> {
      DocumentUtil.executeInBulk(document, ()-> document.replaceString(4, 5, "-"));

      getEditor().getCaretModel().moveToOffset(9);

      DocumentUtil.executeInBulk(document, ()-> document.replaceString(4, 5, "+"));
    });


    checkResultByText("some+text<caret>");
  }
  
  public void testCorrectCaretPositionRestorationAfterMultipleFoldRegionsChange() {
    initText("so<caret>me long text");
    final FoldRegion innerRegion = addCollapsedFoldRegion(0, 4, "...");
    final FoldRegion outerRegion = addCollapsedFoldRegion(0, 9, "...");

    runFoldingOperation(() -> {
      getEditor().getFoldingModel().removeFoldRegion(outerRegion);
      getEditor().getFoldingModel().removeFoldRegion(innerRegion);
    });

    checkResultByText("so<caret>me long text");
  }
  
  public void testEditorSizeCalculationOnOpening() {
    initText("a\nbbb\nccccc");
    getEditor().getSettings().setAdditionalColumnsCount(0);
    getEditor().getSettings().setAdditionalLinesCount(0);
    assertEquals(new Dimension(50, (int)(30 * FontPreferences.DEFAULT_LINE_SPACING)), getEditor().getContentComponent().getPreferredSize());
  }
  
  public void testCollapsingRegionContainingSoftWrap() {
    initText("abcdef abcdef");
    configureSoftWraps(10);
    addCollapsedFoldRegion(0, 13, "...");
    assertEquals(new VisualPosition(0, 3), getEditor().offsetToVisualPosition(13));
  }
  
  public void testSizeRecalculationOnTurningSoftWrapsOff() {
    initText("abc def");
    getEditor().getSettings().setAdditionalColumnsCount(0);
    getEditor().getSettings().setAdditionalLinesCount(0);
    configureSoftWraps(4);
    getEditor().getSettings().setUseSoftWraps(false);
    assertEquals(new Dimension(70, (int)(10 * FontPreferences.DEFAULT_LINE_SPACING)), getEditor().getContentComponent().getPreferredSize());
  }
  
  public void testUpdatingCaretPositionAfterBulkMode() {
    initText("a<caret>bc");
    runWriteCommand(() -> {
      DocumentEx document = (DocumentEx)getEditor().getDocument();
      DocumentUtil.executeInBulk(document, ()-> {
        document.insertString(0, "\n "); // we're changing number of visual lines, and invalidating text layout for caret line
      });
    });

    checkResultByText("\n a<caret>bc");
  }
  
  public void testAllowCaretPositioningInsideSoftWrapOnlyIfVirtualSpaceIsEnabled() {
    initText("abcdef abcdef");
    configureSoftWraps(10);
    mouse().clickAt(0, 10);
    assertEquals(new VisualPosition(0, 7), getEditor().getCaretModel().getVisualPosition());
  }
  
  public void testXYToVisualPositionWhenPrefixIsSet() {
    initText("abc");
    configureSoftWraps(1000); // to make sure soft wrap character width is properly mocked
    ((EditorEx)getEditor()).setPrefixTextAndAttributes(">", new TextAttributes());
    assertEquals(new VisualPosition(0, 0), getEditor().xyToVisualPosition(new Point(1, 0)));
  }
  
  public void testLogicalPositionCacheInvalidationAfterLineDeletion() {
    initText("<caret>\n\t");
    delete();
    assertEquals(new LogicalPosition(0, 4), getEditor().offsetToLogicalPosition(1));
  }
  
  public void testSoftWrapsUpdateAfterEditorWasHidden() {
    initText("long long line");
    configureSoftWraps(6);
    verifySoftWrapPositions(5, 10);

    JViewport viewport = ((EditorEx)getEditor()).getScrollPane().getViewport();
    Dimension normalSize = viewport.getExtentSize();
    viewport.setExtentSize(new Dimension(0, 0));
    runWriteCommand(() -> getEditor().getDocument().deleteString(5, 14));

    viewport.setExtentSize(normalSize);
    
    verifySoftWrapPositions();
  }
  
  public void testUpDownNearDocumentTopAndBottom() {
    initText("abc\nd<caret>ef\nghi");
    up();
    checkResultByText("a<caret>bc\ndef\nghi");
    up();
    checkResultByText("<caret>abc\ndef\nghi");
    down();
    checkResultByText("abc\nd<caret>ef\nghi");
    down();
    checkResultByText("abc\ndef\ng<caret>hi");
    down();
    checkResultByText("abc\ndef\nghi<caret>");
    up();
    checkResultByText("abc\nd<caret>ef\nghi");
  }
  
  public void testScrollingToCaretAtSoftWrap() {
    initText("looooooooooooooooong wooooooooooooooooords");
    configureSoftWraps(10);
    end();
    VisualPosition caretPosition = getEditor().getCaretModel().getCurrentCaret().getVisualPosition();
    Point caretPoint = getEditor().visualPositionToXY(caretPosition);
    Rectangle visibleArea = getEditor().getScrollingModel().getVisibleArea();
    assertTrue(visibleArea.contains(caretPoint));
  }

  public void testChangingHighlightersInBulkModeListener() {
    initText("abcdef");
    Document document = getEditor().getDocument();
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void bulkUpdateFinished(@NotNull Document document) {
        getEditor().getMarkupModel().addRangeHighlighter(null, 7, 8, 0, HighlighterTargetArea.EXACT_RANGE);
      }
    }, getTestRootDisposable());
    runWriteCommand(() -> DocumentUtil.executeInBulk(document, ()-> document.insertString(3, "\n\n")));
    RangeHighlighter[] highlighters = getEditor().getMarkupModel().getAllHighlighters();
    assertEquals(1, highlighters.length);
    assertEquals(7, highlighters[0].getStartOffset());
    assertEquals(8, highlighters[0].getEndOffset());
  }

  public void testChangingHighlightersAfterClearingFoldingsDuringFoldingBatchUpdate() {
    initText("abc\n\ndef");
    addCollapsedFoldRegion(2, 6, "...");
    runFoldingOperation(() -> {
      ((FoldingModelEx)getEditor().getFoldingModel()).clearFoldRegions();
      getEditor().getMarkupModel().addRangeHighlighter(7, 8, 0,
                                                       new TextAttributes(null, null, null, null, Font.BOLD),
                                                       HighlighterTargetArea.EXACT_RANGE);
    });
    RangeHighlighter[] highlighters = getEditor().getMarkupModel().getAllHighlighters();
    assertEquals(1, highlighters.length);
    assertEquals(7, highlighters[0].getStartOffset());
    assertEquals(8, highlighters[0].getEndOffset());
  }

  public void testShiftPressedBeforeDragOverLineNumbersIsFinished() {
    initText("abc\ndef\nghi");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000); // enable drag testing
    mouse().pressAtLineNumbers(0).dragToLineNumbers(2).shift().release();
    checkResultByText("<selection>abc\ndef\nghi</selection>");
  }

  public void testScrollingInEditorOfSmallHeight() {
    initText("abc\n<caret>");
    int heightInPixels = (int)(getEditor().getLineHeight() * 1.5);
    EditorTestUtil.setEditorVisibleSizeInPixels(getEditor(),
                                                1000 * EditorUtil.getSpaceWidth(Font.PLAIN, getEditor()),
                                                heightInPixels);
    getEditor().getSettings().setAnimatedScrolling(false);
    type('a');
    assertEquals(heightInPixels - getEditor().getLineHeight(), getEditor().getScrollingModel().getVerticalScrollOffset());
    type('b');
    assertEquals(heightInPixels - getEditor().getLineHeight(), getEditor().getScrollingModel().getVerticalScrollOffset());
  }

  public void testEditorWithSoftWrapsBecomesVisibleAfterDocumentTextRemoval() {
    initText("abc def ghi");
    configureSoftWraps(3);
    JViewport viewport = ((EditorEx)getEditor()).getScrollPane().getViewport();
    viewport.setExtentSize(new Dimension()); // emulate editor becoming invisible
    runWriteCommand(() -> {
        Document document = getEditor().getDocument();
        document.deleteString(0, document.getTextLength());
    });
    viewport.setExtentSize(new Dimension(1000, 1000)); // editor becomes visible
    verifySoftWrapPositions();
  }

  public void testDocumentChangeAfterEditorDisposal() {
    EditorFactory editorFactory = EditorFactory.getInstance();

    Document document = editorFactory.createDocument("ab");
    Editor editor = editorFactory.createEditor(document);
    try {
      editor.getCaretModel().moveToOffset(1);
    }
    finally {
      editorFactory.releaseEditor(editor);
    }

    runWriteCommand(() -> document.setText("cd")); // should run without throwing an exception
  }

  public void testLineLengthMatchingLogicalPositionCacheFrequency() {
    initText("\t" + StringUtil.repeat(" ", 1023));
    assertEquals(new LogicalPosition(0, 1027), getEditor().offsetToLogicalPosition(1024));
  }

  public void testSpecialCaseOfCaretPositionUpdateOnFolding() {
    initText("abc\ndef\ngh<caret>i");
    FoldRegion region = addCollapsedFoldRegion(1, 11, "...");
    runWriteCommand(()-> getEditor().getDocument().deleteString(7, 8));
    runFoldingOperation(()-> region.setExpanded(true));
    runFoldingOperation(()-> region.setExpanded(false));
    assertFalse(region.isExpanded());
  }

  public void testEditingNearInlayInBulkMode() {
    initText("a<caret>bc");
    addInlay(1);
    runWriteCommand(()-> DocumentUtil.executeInBulk(getEditor().getDocument(),
                                                    ()-> getEditor().getDocument().insertString(1, " ")));
    checkResultByText("a<caret> bc");
    assertTrue(getEditor().getInlayModel().hasInlineElementAt(1));
  }

  public void testCoordinateConversionsAroundSurrogatePair() {
    initText("a" + SURROGATE_PAIR + "b");
    assertEquals(new LogicalPosition(0, 2), getEditor().offsetToLogicalPosition(3));
    assertEquals(3, getEditor().logicalPositionToOffset(new LogicalPosition(0, 2)));
    assertEquals(new VisualPosition(0, 2), getEditor().logicalToVisualPosition(new LogicalPosition(0, 2)));
    assertEquals(new LogicalPosition(0, 2), getEditor().visualToLogicalPosition(new VisualPosition(0, 2)));
  }

  public void testCreationOfSurrogatePairByMergingDividedParts() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> {
      getEditor().getDocument().setText(HIGH_SURROGATE + " " + LOW_SURROGATE);
      getEditor().getDocument().deleteString(1, 2);
    });
    checkResultByText(SURROGATE_PAIR);
    assertEquals(new LogicalPosition(0, 1), getEditor().offsetToLogicalPosition(2));
  }

  public void testCaretDoesntGetInsideSurrogatePair() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> getEditor().getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    getEditor().getCaretModel().moveToOffset(2);
    runWriteCommand(() -> ((DocumentEx)getEditor().getDocument()).moveText(2, 3, 1));
    assertFalse(DocumentUtil.isInsideSurrogatePair(getEditor().getDocument(), getEditor().getCaretModel().getOffset()));
  }

  public void testFoldingBoundaryDoesntGetInsideSurrogatePair() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> getEditor().getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    addFoldRegion(2, 3, "...");
    runWriteCommand(() -> ((DocumentEx)getEditor().getDocument()).moveText(2, 3, 1));
    FoldRegion[] foldRegions = getEditor().getFoldingModel().getAllFoldRegions();
    assertFalse(foldRegions.length > 0 && DocumentUtil.isInsideSurrogatePair(getEditor().getDocument(), foldRegions[0].getStartOffset()));
  }

  public void testRemovalOfNonExpandingFoldingRegion() {
    initText("a\nb");
    FoldingModelEx model = (FoldingModelEx)getEditor().getFoldingModel();
    Ref<FoldRegion> regionRef = new Ref<>();
    runFoldingOperation(() -> {
      FoldRegion region = model.createFoldRegion(0, 3, "...", null, true);
      assertNotNull(region);
      assertTrue(region.isValid());
      assertFalse(region.isExpanded());
      regionRef.set(region);
    });
    runFoldingOperation(() -> model.removeFoldRegion(regionRef.get()));
    assertEquals(new VisualPosition(1, 1), getEditor().offsetToVisualPosition(3));
  }
  
  public void testPasteWithStickySelection() {
    initText("<selection>abc<caret></selection>\ndef");
    copy();
    down();
    home();
    ((EditorEx)getEditor()).setStickySelection(true);
    right();
    right();
    right();
    paste();
    checkResultByText("abc\nabc<caret>");
  }
  
  public void testPasteInVirtualSpaceWhenSelectionExists() {
    initText("foo");
    getEditor().getSettings().setVirtualSpace(true);
    mouse().clickAt(0, 10);
    executeAction(IdeActions.ACTION_SELECT_ALL);
    CopyPasteManager.getInstance().setContents(new StringSelection("bar"));
    paste();
    checkResultByText("bar<caret>");
  }

  public void testMoveTextNearInlayAtBrokenSurrogates() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> getEditor().getDocument().setText(LOW_SURROGATE + HIGH_SURROGATE));
    Inlay inlay = addInlay(2);
    FoldRegion region = addCollapsedFoldRegion(1, 2, "...");
    
    runWriteCommand(() -> ((DocumentEx)getEditor().getDocument()).moveText(1, 2, 0));
    
    assertFalse(inlay.isValid() && inlay.getOffset() == 1);
    assertFalse(region.isValid() && (region.getStartOffset() == 1 || region.getEndOffset() == 1));
  }

  public void testStickySelectionAtDocumentEnd() {
    initText("ab<caret>");
    ((EditorEx)getEditor()).setStickySelection(true);
    left();
    checkResultByText("a<selection><caret>b</selection>");
  }

  public void testDocumentUpdateAtCaretInVirtualSpace() {
    initText("ab\ncd");
    getEditor().getSettings().setVirtualSpace(true);
    mouse().clickAt(0, 4);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
    checkResultByText("ab<caret>cd");
    assertEquals(new LogicalPosition(0, 2), getEditor().getCaretModel().getLogicalPosition());
    assertEquals(new VisualPosition(0, 2), getEditor().getCaretModel().getVisualPosition());
  }

  public void testTypingInOverwriteModeWithMultilineSelection() {
    initText("a<selection>b\nc<caret></selection>d");
    ((EditorEx)getEditor()).setInsertMode(false);
    type(' ');
    checkResultByText("a <caret>");
  }

  public void testSettingPromptDoesNotCreateSoftWraps() {
    initText(StringUtil.repeat("a ", 1000));
    ((EditorEx)getEditor()).setPrefixTextAndAttributes(">", new TextAttributes());
    runWriteCommand(() -> getEditor().getDocument().deleteString(0, getEditor().getDocument().getTextLength()));
    assertEquals(1, ((EditorImpl)getEditor()).getVisibleLineCount());
  }

  public void testDragInsideSelectionWithDndDisabled() {
    initText("abcdef");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
    getEditor().getSettings().setDndEnabled(false);
    EditorMouseFixture mouse = mouse();
    mouse.pressAt(0, 0).dragTo(0, 3).release();
    checkResultByText("<selection>abc<caret></selection>def");
    mouse.pressAt(0, 1);
    checkResultByText("a<caret>bcdef");
    mouse.dragTo(0, 4);
    checkResultByText("a<selection>bcd<caret></selection>ef");
    mouse.release();
    checkResultByText("a<selection>bcd<caret></selection>ef");
  }

  public void testCreateRectangularSelectionWithDndDisabled() {
    initText("ab\ncd<caret>");
    getEditor().getSettings().setDndEnabled(false);
    mouse().alt().shift().middle().clickAt(0, 0);
    checkResultByText("<selection><caret>ab</selection>\n<selection><caret>cd</selection>");
  }

  public void testActionsFromPopupMenuCanBeInvokedForSelection() {
    initText("abcd");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
    getEditor().getSettings().setDndEnabled(false);
    EditorMouseFixture mouse = mouse();
    
    mouse.pressAt(0, 1).dragTo(0, 3).release();
    mouse.right().pressAt(0, 2);
    checkResultByText("a<selection>bc</selection>d");
    mouse.release();
  }

  public void testTripleClickWithDndDisabled() {
    initText("some text");
    getEditor().getSettings().setDndEnabled(false);
    mouse().tripleClickAt(0, 2);
    checkResultByText("<selection>some text</selection>");
  }

  public void testSelectionAfterSelectingWholeLine() {
    initText("line 1\nline 2");
    mouse().tripleClickAt(0, 2);
    checkResultByText("<selection>li<caret>ne 1\n</selection>line 2");
    delete();
    checkResultByText("<caret>line 2");
    rightWithSelection();
    checkResultByText("<selection>l<caret></selection>ine 2");
  }

  public void testRightClickOutsideSelectionRemovesIt() {
    initText("<selection>some<caret></selection> text");
    mouse().right().clickAt(0, 6);
    checkResultByText("some t<caret>ext");
  }

  public void testRightClickOutsideButVeryCloseToSelectionDoesNotRemoveIt() {
    initText("<selection>some<caret></selection> text");
    mouse().right().clickAtXY(42, 5);
    checkResultByText("<selection>some<caret></selection> text");
  }

  public void testDragStartingBeyondSelectedLineEnd() {
    initText("<selection>line1</selection>\nline2");
    setEditorVisibleSize(100, 100);
    mouse().pressAt(0, 10).dragTo(1, 10).release();
    checkResultByText("line1<selection>\nline2</selection>");
  }

  public void testSizeUpdateAfterMovingFragmentWithFolding() {
    initText("line\nline\nline\nline\nlong line");
    getEditor().getSettings().setAdditionalColumnsCount(0);
    addCollapsedFoldRegion(19, 25, "...");
    runWriteCommand(() -> ((EditorEx)getEditor()).getDocument().moveText(19, 25, 0));
    assertEquals(80, getEditor().getContentComponent().getPreferredSize().width);
  }

  public void testEditorIsNotScrolledOnSoftWrappingIfNotScrolledPreviously() {
    initText("long long line");
    EditorTestUtil.configureSoftWrapsAndViewport(getEditor(), 100, 1);
    EditorTestUtil.configureSoftWrapsAndViewport(getEditor(), 10, 1);
    assertEquals(0, getEditor().getScrollingModel().getVerticalScrollOffset());
  }

  public void testClickOnBlockInlayDoesNotMoveCaret() {
    initText("text");
    addBlockInlay(0, true, 10 * TEST_CHAR_WIDTH);
    mouse().clickAtXY(2 * TEST_CHAR_WIDTH, 0);
    checkResultByText("<caret>text");
  }

  public void testDragInBlockInlayDoesNotCreateSelection() {
    initText("text");
    addBlockInlay(0, true, 10 * TEST_CHAR_WIDTH);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
    mouse().pressAtXY(2 * TEST_CHAR_WIDTH, 0).dragToXY(4 * TEST_CHAR_WIDTH, 0).release();
    checkResultByText("<caret>text");
  }

  public void testDragBeyondEditorTopWithBlockInlay() {
    initText("text");
    addBlockInlay(0, true);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
    mouse().pressAtXY(2 * TEST_CHAR_WIDTH, (int)(getEditor().getLineHeight() * 1.5)).dragToXY(-1, -1).release();
    checkResultByText("<selection><caret>te</selection>xt");
  }

  public void testCaretMovementAtFoldRegionWithEmptyPlaceholder() {
    initText("<caret>abc");
    addCollapsedFoldRegion(1, 2, "");
    right();
    Caret caret = getEditor().getCaretModel().getPrimaryCaret();
    assertEquals(2, caret.getOffset());
    assertEquals(new LogicalPosition(0, 2), caret.getLogicalPosition());
    assertEquals(new VisualPosition(0, 1), caret.getVisualPosition());
    right();
    assertEquals(3, caret.getOffset());
    assertEquals(new LogicalPosition(0, 3), caret.getLogicalPosition());
    assertEquals(new VisualPosition(0, 2), caret.getVisualPosition());
  }

  public void testDragStartingAtBlockInlay() {
    initText("abc<caret>");
    addBlockInlay(0, true, 10);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
    mouse().pressAtXY(0, 0).dragToXY(0, getEditor().getLineHeight()).release();
    checkResultByText("abc<caret>");
  }

  public void testMouseDraggingWithCamelHumpsDisabledForMouse() {
    boolean savedOption = EditorSettingsExternalizable.getInstance().isCamelWords();
    try {
      EditorSettingsExternalizable.getInstance().setCamelWords(true);
      initText("AbcDefGhi");
      EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
      getEditor().getSettings().setMouseClickSelectionHonorsCamelWords(false);
      mouse().doubleClickNoReleaseAt(0, 4).dragTo(0, 5).release();
      checkResultByText("<selection>AbcDefGhi<caret></selection>");
    }
    finally {
      EditorSettingsExternalizable.getInstance().setCamelWords(savedOption);
    }
  }

  public void testSettingFontPreferences() {
    initText("");
    EditorColorsScheme colorsScheme = getEditor().getColorsScheme();

    FontPreferencesImpl preferences = new FontPreferencesImpl();
    preferences.register("CustomFont", 32);
    preferences.setUseLigatures(true);
    colorsScheme.setFontPreferences(preferences);

    FontPreferences p = colorsScheme.getFontPreferences();
    assertEquals(Collections.singletonList("CustomFont"), p.getRealFontFamilies());
    assertEquals(32, p.getSize("CustomFont"));
    assertTrue(p.useLigatures());

    FontPreferencesImpl preferences2 = new FontPreferencesImpl();
    preferences2.register("CustomFont2", 23);
    preferences2.setUseLigatures(false);
    colorsScheme.setFontPreferences(preferences2);

    FontPreferences p2 = colorsScheme.getFontPreferences();
    assertEquals(Collections.singletonList("CustomFont2"), p2.getRealFontFamilies());
    assertEquals(23, p2.getSize("CustomFont2"));
    assertFalse(p.useLigatures());
  }

  public void testDocumentChangeAfterWidthChange() {
    initText("Some long line of text");
    configureSoftWraps(15);

    // emulate adding editor to a wide component
    SoftWrapApplianceManager.VisibleAreaWidthProvider widthProvider =
      ((SoftWrapModelImpl)getEditor().getSoftWrapModel()).getApplianceManager().getWidthProvider();
    ((EditorTestUtil.TestWidthProvider)widthProvider).setVisibleAreaWidth(1_000_000);
    new JPanel().add(getEditor().getComponent());

    runWriteCommand(() -> getEditor().getDocument().insertString(0, " "));
    verifySoftWrapPositions();
  }

  public void testClickOnBlockInlayDoesNotRemoveSelection() {
    initText("<selection>text<caret></selection>");
    addBlockInlay(0, true, 100);
    mouse().clickAtXY(50, getEditor().getLineHeight() / 2);
    checkResultByText("<selection>text<caret></selection>");
  }

  public void testWordSelectionOnMouseDragAroundPunctuation() {
    initText("((some (text)))");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 100, 100);
    mouse().doubleClickNoReleaseAt(0, 4).dragTo(0, 12).release();
    checkResultByText("((<selection>some (text)<caret></selection>))");
  }

  public void testSurrogatePairInputInOverwriteMode() {
    initText("ab<caret>cd");
    ((EditorEx)getEditor()).setInsertMode(false);
    JComponent component = getEditor().getContentComponent();
    component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                 new AttributedString("\uD83D\uDE00" /* 'GRINNING FACE' emoji (U+1F600) */).getIterator(),
                                                 2, null, null));
    checkResultByText("ab\uD83D\uDE00d");
  }

  public void testInputMethodComposing() {
    initText("hello<caret>");
    JComponent component = getEditor().getContentComponent();
    component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                 new AttributedString("\u3145" /* ㅅ */).getIterator(),
                                                 0, TextHitInfo.afterOffset(0),
                                                 TextHitInfo.afterOffset(-1)));
    component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                 new AttributedString("\u3146" /* ㅆ */).getIterator(),
                                                 0, TextHitInfo.afterOffset(0),
                                                 TextHitInfo.afterOffset(-1)));
    checkResultByText("hello\u3146");
  }

  public void testInputMethodCaretMove() {
    initText("hello<caret>");
    JComponent component = getEditor().getContentComponent();
    component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                 new AttributedString("\u3145" /* ㅅ */).getIterator(),
                                                 0, null, null));
    component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                 new AttributedString("\u3146" /* ㅆ */).getIterator(),
                                                 0, null, null));
    mouse().clickAtXY(0, getEditor().getLineHeight() / 2);
    checkResultByText("<caret>hello\u3146");
  }

  public void testScrollingToCaretWithWideCharacters() {
    FontLayoutService.setInstance(new MockFontLayoutService(cp -> cp == 'Z' ? 2 * TEST_CHAR_WIDTH : TEST_CHAR_WIDTH,
                                                            TEST_LINE_HEIGHT, TEST_DESCENT));
    initText(StringUtil.repeatSymbol('Z', 500) + "<caret>");
    setEditorVisibleSize(100, 100);
    type(' ');
    Rectangle visibleArea = getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished();
    Point caretPosition = getEditor().visualPositionToXY(getEditor().getCaretModel().getVisualPosition());
    assertTrue(visibleArea.contains(caretPosition));
  }

  public void testMouseSelectionWithBlockCaret() {
    Registry.get("editor.block.caret.selection.vim-like").setValue(true, getTestRootDisposable());
    initText("abcdef");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000);  // enable drag testing
    getEditor().getSettings().setBlockCursor(true);
    int y = getEditor().getLineHeight() / 2;

    mouse().pressAtXY(3 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("abc<caret>def");
    mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("abc<caret>def");
    mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("ab<selection><caret>cd</selection>ef");
    mouse().dragToXY(TEST_CHAR_WIDTH + 1, y);
    checkResultByText("a<selection><caret>bcd</selection>ef");
    mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("ab<selection><caret>cd</selection>ef");
    mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("abc<selection><caret>d</selection>ef");
    mouse().dragToXY(4 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("abc<selection>d<caret>e</selection>f");
    mouse().dragToXY(5 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("abc<selection>de<caret>f</selection>");
    mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("ab<selection><caret>cd</selection>ef");
    mouse().dragToXY(5 * TEST_CHAR_WIDTH + 1, y);
    checkResultByText("abc<selection>de<caret>f</selection>");
  }

  public void testMouseSelectionAtLineEndWithBlockCaret() {
    Registry.get("editor.block.caret.selection.vim-like").setValue(true, getTestRootDisposable());
    initText("abc\ndef");
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000);  // enable drag testing
    getEditor().getSettings().setBlockCursor(true);
    int line1Y = getEditor().getLineHeight() / 2;
    int line2Y = getEditor().getLineHeight() * 3 / 2;

    mouse().pressAtXY(3 * TEST_CHAR_WIDTH + 1, line1Y);
    checkResultByText("abc<caret>\ndef");
    mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, line1Y);
    checkResultByText("abc<caret>\ndef");
    mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, line1Y);
    checkResultByText("ab<selection><caret>c\n</selection>def");
    mouse().dragToXY(TEST_CHAR_WIDTH + 1, line1Y);
    checkResultByText("a<selection><caret>bc\n</selection>def");
    mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, line1Y);
    checkResultByText("ab<selection><caret>c\n</selection>def");
    mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, line1Y);
    checkResultByText("abc<selection><caret>\n</selection>def");
    mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, line2Y);
    checkResultByText("abc<selection>\ndef<caret></selection>");
    mouse().dragToXY(1, line2Y);
    checkResultByText("abc<selection>\n<caret>d</selection>ef");
    mouse().dragToXY(1, line1Y);
    checkResultByText("<selection><caret>abc\n</selection>def");
  }

  public void testPressAndHoldInputMethodOnMac() {
    if (!SystemInfo.isMac) return; // macOS-specific test
    initText("<caret>b");
    JComponent component = getEditor().getContentComponent();
    // 'A' key pressed
    dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a', KeyEvent.KEY_LOCATION_STANDARD));
    dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a', KeyEvent.KEY_LOCATION_UNKNOWN));
    checkResultByText("a<caret>b");
    // popup with diacritic appears while key is held pressed
    requestSelectedTextFromEditorViaImeApi();
    checkResultByText("a<caret>b");
    // 'A' key released
    dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_RELEASED, 1, 0, KeyEvent.VK_A, 'a', KeyEvent.KEY_LOCATION_STANDARD));
    checkResultByText("a<caret>b");
    // 'Right arrow' key pressed to select first variant from the popup
    requestSelectedTextFromEditorViaImeApi();
    dispatchEventToEditor(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, 2,
                                                 new AttributedString("\u00e0" /* LATIN SMALL LETTER A WITH GRAVE */).getIterator(), 0,
                                                 null, TextHitInfo.beforeOffset(0)));
    checkResultByText("\u00e0b"); // not checking caret/selection state, as we don't need to enforce the behaviour as of test creation time
    // 'Right arrow' key released
    dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_RELEASED, 3, 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED,
                                         KeyEvent.KEY_LOCATION_STANDARD));
    checkResultByText("\u00e0b"); // not checking caret/selection state, as we don't need to enforce the behaviour as of test creation time
    // 'Enter' key pressed to confirm selected variant
    requestSelectedTextFromEditorViaImeApi();
    dispatchEventToEditor(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, 4,
                                                 new AttributedString("\u00e0" /* LATIN SMALL LETTER A WITH GRAVE */).getIterator(), 1,
                                                 TextHitInfo.afterOffset(0), TextHitInfo.afterOffset(0)));
    checkResultByText("\u00e0<caret>b");
    // 'Enter' key released
    dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_RELEASED, 5, 0, KeyEvent.VK_ENTER, '\n', KeyEvent.KEY_LOCATION_STANDARD));
    checkResultByText("\u00e0<caret>b");
  }

  public void testSelectionOutsideVisibleAreaWithBlockCaret() throws Exception {
    Registry.get("editor.block.caret.selection.vim-like").setValue(true, getTestRootDisposable());
    initText(StringUtil.repeatSymbol('\n', 20));
    EditorTestUtil.setEditorVisibleSize(getEditor(), 10, 10);  // enable drag testing
    getEditor().getSettings().setBlockCursor(true);
    Registry.get("editor.scrolling.animation.interval.ms").setValue(1, getTestRootDisposable()); // make scrolling faster

    EditorMouseFixture mouse = mouse();
    mouse.pressAt(0, 0).dragTo(15, 0);

    // scroll till all text is selected
    while (getEditor().getSelectionModel().getSelectionEnd() != getEditor().getDocument().getTextLength()) {
      UIUtil.dispatchAllInvocationEvents();
    }

    Rectangle visibleArea = getEditor().getScrollingModel().getVisibleArea();
    mouse.dragToXY((int)visibleArea.getCenterX(), (int)visibleArea.getCenterY()).release(); // stop scrolling

    assertEquals(0, getEditor().getSelectionModel().getSelectionStart());
    assertTrue(getEditor().getSelectionModel().getSelectionEnd() > 0);
  }

  public void testLeadSelectionPosition() throws Exception {
    initText("<caret>abc");
    Caret caret = getEditor().getCaretModel().getPrimaryCaret();

    caret.moveToOffset(1);
    caret.setSelection(0, 1);

    assertEquals(0, caret.getLeadSelectionOffset());
    assertEquals(new VisualPosition(0, 0), caret.getLeadSelectionPosition());
  }

  private void dispatchEventToEditor(AWTEvent event) {
    // this method ensures key events are dispatched to editor component, even if it's not focused
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(getEditor().getContentComponent(), event);
  }

  private void requestSelectedTextFromEditorViaImeApi() {
    getEditor().getContentComponent().getInputMethodRequests().getSelectedText(null);
  }
}
