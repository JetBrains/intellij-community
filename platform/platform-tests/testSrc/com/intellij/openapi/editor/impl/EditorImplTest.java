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

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class EditorImplTest extends AbstractEditorTest {
  public void testPositionCalculationForZeroWidthChars() {
    initText("some\u2044text");
    VisualPosition pos = new VisualPosition(0, 6);
    VisualPosition recalculatedPos = myEditor.xyToVisualPosition(myEditor.visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }

  public void testPositionCalculationOnEmptyLine() {
    initText("text with\n" +
         "\n" +
         "empty line");
    VisualPosition pos = new VisualPosition(1, 0);
    VisualPosition recalculatedPos = myEditor.xyToVisualPosition(myEditor.visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }

  public void testShiftDragAddsToSelection() {
    initText("A quick brown fox");
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000); // enable drag testing
    mouse().clickAt(0, 1);
    mouse().shift().pressAt(0, 2).dragTo(0, 3).release();
    checkResultByText("A<selection> q<caret></selection>uick brown fox");
  }

  public void testSoftWrapsRecalculationInASpecificCase() {
    configureFromFileText(getTestName(false) + ".java",
                          "<selection>class Foo {\n" +
                          "\t@Override\n" +
                          "\tpublic boolean equals(Object other) {\n" +
                          "\t\treturn this == other;\n" +
                          "\t}\n" +
                          "}</selection>");
    CodeFoldingManager.getInstance(ourProject).buildInitialFoldings(myEditor);
    configureSoftWraps(32);

    // verify initial state
    assertEquals(4, EditorUtil.getTabSize(myEditor));
    assertEquals("[FoldRegion +(59:64), placeholder=' { ', FoldRegion +(85:88), placeholder=' }']", myEditor.getFoldingModel().toString());
    verifySoftWrapPositions(52, 85);

    runWriteCommand(() -> {
      Document document = myEditor.getDocument();
      for (int i = document.getLineCount() - 1; i >= 0; i--) {
        document.insertString(document.getLineStartOffset(i), "//");
      }
    });


    verifySoftWrapPositions(58, 93);
  }

  public void testCorrectVisibleLineCountCalculation() {
    initText("line containing FOLDED_REGION\n" +
         "next <caret>line\n" +
         "last line");
    foldOccurrences("FOLDED_REGION", "...");
    configureSoftWraps(16); // wrap right at folded region start
    verifySoftWrapPositions(16);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    checkResultByText("line containing FOLDED_REGION\n" +
                      "next line\n" +
                      "last <caret>line");
  }

  public void testInsertingFirstTab() {
    initText(" <caret>space-indented line");
    configureSoftWraps(100);
    myEditor.getSettings().setUseTabCharacter(true);

    executeAction(IdeActions.ACTION_EDITOR_TAB);
    checkResultByText(" \t<caret>space-indented line");
  }

  public void testNoExceptionDuringBulkModeDocumentUpdate() {
    initText("something");
    DocumentEx document = (DocumentEx)myEditor.getDocument();
    runWriteCommand(() -> {
      DocumentUtil.executeInBulk(document, true, ()-> {
        document.setText("something\telse");
      });
    });

    checkResultByText("something\telse");
  }

  public void testCaretMergeDuringBulkModeDocumentUpdate() {
    initText("a<selection>bcdef<caret></selection>g\n" +
             "a<selection>bcdef<caret></selection>g");
    runWriteCommand(() -> {
      DocumentEx document = (DocumentEx)myEditor.getDocument();
      DocumentUtil.executeInBulk(document, true, ()-> {
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

    assertEquals(new VisualPosition(0, 5), myEditor.logicalToVisualPosition(new LogicalPosition(0, 3)));
  }
  
  public void testNavigationIntoFoldedRegionWithSoftWrapsEnabled() {
    initText("something");
    addCollapsedFoldRegion(4, 8, "...");
    configureSoftWraps(1000);
    
    myEditor.getCaretModel().moveToOffset(5);
    
    assertEquals(new VisualPosition(0, 5), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testNavigationInsideNonNormalizedLineTerminator() {
    initText("");
    ((DocumentImpl)myEditor.getDocument()).setAcceptSlashR(true);
    runWriteCommand(() -> myEditor.getDocument().insertString(0, "abc\r\ndef"));

    myEditor.getCaretModel().moveToOffset(4);
    
    assertEquals(new LogicalPosition(0, 3), myEditor.getCaretModel().getLogicalPosition());
    assertEquals(new VisualPosition(0, 3), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testSoftWrapModeUpdateDuringBulkModeChange() {
    initText("long long line<caret>");
    configureSoftWraps(12);
    DocumentEx document = (DocumentEx)myEditor.getDocument();
    runWriteCommand(() -> {
      DocumentUtil.executeInBulk(document, true, ()-> {
        document.replaceString(4, 5, "-");
      });
    });

    assertEquals(new VisualPosition(1, 5), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testSuccessiveBulkModeOperations() {
    initText("some text");
    DocumentEx document = (DocumentEx)myEditor.getDocument();

    runWriteCommand(() -> {
      DocumentUtil.executeInBulk(document, true, ()-> {
        document.replaceString(4, 5, "-");
      });

      myEditor.getCaretModel().moveToOffset(9);

      DocumentUtil.executeInBulk(document, true, ()-> {
        document.replaceString(4, 5, "+");
      });
    });


    checkResultByText("some+text<caret>");
  }
  
  public void testCorrectCaretPositionRestorationAfterMultipleFoldRegionsChange() {
    initText("so<caret>me long text");
    final FoldRegion innerRegion = addCollapsedFoldRegion(0, 4, "...");
    final FoldRegion outerRegion = addCollapsedFoldRegion(0, 9, "...");

    runFoldingOperation(() -> {
      myEditor.getFoldingModel().removeFoldRegion(outerRegion);
      myEditor.getFoldingModel().removeFoldRegion(innerRegion);
    });

    checkResultByText("so<caret>me long text");
  }
  
  public void testEditorSizeCalculationOnOpening() {
    initText("a\nbbb\nccccc");
    myEditor.getSettings().setAdditionalColumnsCount(0);
    myEditor.getSettings().setAdditionalLinesCount(0);
    assertEquals(new Dimension(50, 30), myEditor.getContentComponent().getPreferredSize());
  }
  
  public void testCollapsingRegionContainingSoftWrap() {
    initText("abcdef abcdef");
    configureSoftWraps(10);
    addCollapsedFoldRegion(0, 13, "...");
    assertEquals(new VisualPosition(0, 3), myEditor.offsetToVisualPosition(13));
  }
  
  public void testSizeRecalculationOnTurningSoftWrapsOff() {
    initText("abc def");
    myEditor.getSettings().setAdditionalColumnsCount(0);
    myEditor.getSettings().setAdditionalLinesCount(0);
    configureSoftWraps(4);
    myEditor.getSettings().setUseSoftWraps(false);
    assertEquals(new Dimension(70, 10), myEditor.getContentComponent().getPreferredSize());
  }
  
  public void testUpdatingCaretPositionAfterBulkMode() {
    initText("a<caret>bc");
    runWriteCommand(() -> {
      DocumentEx document = (DocumentEx)myEditor.getDocument();
      DocumentUtil.executeInBulk(document, true, ()-> {
        document.insertString(0, "\n "); // we're changing number of visual lines, and invalidating text layout for caret line
      });
    });

    checkResultByText("\n a<caret>bc");
  }
  
  public void testAllowCaretPositioningInsideSoftWrapOnlyIfVirtualSpaceIsEnabled() {
    initText("abcdef abcdef");
    configureSoftWraps(10);
    mouse().clickAt(0, 10);
    assertEquals(new VisualPosition(0, 7), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testXYToVisualPositionWhenPrefixIsSet() {
    initText("abc");
    configureSoftWraps(1000); // to make sure soft wrap character width is properly mocked
    ((EditorEx)myEditor).setPrefixTextAndAttributes(">", new TextAttributes());
    assertEquals(new VisualPosition(0, 0), myEditor.xyToVisualPosition(new Point(1, 0)));
  }
  
  public void testLogicalPositionCacheInvalidationAfterLineDeletion() {
    initText("<caret>\n\t");
    delete();
    assertEquals(new LogicalPosition(0, 4), myEditor.offsetToLogicalPosition(1));
  }
  
  public void testSoftWrapsUpdateAfterEditorWasHidden() {
    initText("long long line");
    configureSoftWraps(6);
    verifySoftWrapPositions(5, 10);

    JViewport viewport = ((EditorEx)myEditor).getScrollPane().getViewport();
    Dimension normalSize = viewport.getExtentSize();
    viewport.setExtentSize(new Dimension(0, 0));
    runWriteCommand(() -> myEditor.getDocument().deleteString(5, 14));

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
    VisualPosition caretPosition = myEditor.getCaretModel().getCurrentCaret().getVisualPosition();
    Point caretPoint = myEditor.visualPositionToXY(caretPosition);
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    assertTrue(visibleArea.contains(caretPoint));
  }

  public void testChangingHighlightersInBulkModeListener() {
    DocumentBulkUpdateListener.Adapter listener = new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateFinished(@NotNull Document doc) {
        if (doc == myEditor.getDocument()) {
          myEditor.getMarkupModel().addRangeHighlighter(7, 8, 0, null, HighlighterTargetArea.EXACT_RANGE);
        }
      }
    };
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DocumentBulkUpdateListener.TOPIC, listener);
    initText("abcdef");
    DocumentEx document = (DocumentEx)myEditor.getDocument();
    runWriteCommand(() -> {
      DocumentUtil.executeInBulk(document, true, ()-> {
        document.insertString(3, "\n\n");
      });
    });
    RangeHighlighter[] highlighters = myEditor.getMarkupModel().getAllHighlighters();
    assertEquals(1, highlighters.length);
    assertEquals(7, highlighters[0].getStartOffset());
    assertEquals(8, highlighters[0].getEndOffset());
  }

  public void testChangingHighlightersAfterClearingFoldingsDuringFoldingBatchUpdate() {
    initText("abc\n\ndef");
    addCollapsedFoldRegion(2, 6, "...");
    runFoldingOperation(() -> {
      ((FoldingModelEx)myEditor.getFoldingModel()).clearFoldRegions();
      myEditor.getMarkupModel().addRangeHighlighter(7, 8, 0, new TextAttributes(null, null, null, null, Font.BOLD),
                                                    HighlighterTargetArea.EXACT_RANGE);
    });
    RangeHighlighter[] highlighters = myEditor.getMarkupModel().getAllHighlighters();
    assertEquals(1, highlighters.length);
    assertEquals(7, highlighters[0].getStartOffset());
    assertEquals(8, highlighters[0].getEndOffset());
  }

  public void testShiftPressedBeforeDragOverLineNumbersIsFinished() {
    initText("abc\ndef\nghi");
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000); // enable drag testing
    mouse().pressAtLineNumbers(0).dragToLineNumbers(2).shift().release();
    checkResultByText("<selection>abc\ndef\nghi</selection>");
  }

  public void testScrollingInEditorOfSmallHeight() {
    initText("abc\n<caret>");
    int heightInPixels = (int)(myEditor.getLineHeight() * 1.5);
    EditorTestUtil.setEditorVisibleSizeInPixels(myEditor,
                                                1000 * EditorUtil.getSpaceWidth(Font.PLAIN, myEditor),
                                                heightInPixels);
    myEditor.getSettings().setAnimatedScrolling(false);
    type('a');
    assertEquals(heightInPixels - myEditor.getLineHeight(), myEditor.getScrollingModel().getVerticalScrollOffset());
    type('b');
    assertEquals(heightInPixels - myEditor.getLineHeight(), myEditor.getScrollingModel().getVerticalScrollOffset());
  }

  public void testEditorWithSoftWrapsBecomesVisibleAfterDocumentTextRemoval() {
    initText("abc def ghi");
    configureSoftWraps(3);
    JViewport viewport = ((EditorEx)myEditor).getScrollPane().getViewport();
    viewport.setExtentSize(new Dimension()); // emulate editor becoming invisible
    runWriteCommand(() -> {
        Document document = myEditor.getDocument();
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
    assertEquals(new LogicalPosition(0, 1027), myEditor.offsetToLogicalPosition(1024));
  }

  public void testSpecialCaseOfCaretPositionUpdateOnFolding() {
    initText("abc\ndef\ngh<caret>i");
    FoldRegion region = addCollapsedFoldRegion(1, 11, "...");
    runWriteCommand(()-> myEditor.getDocument().deleteString(7, 8));
    runFoldingOperation(()-> region.setExpanded(true));
    runFoldingOperation(()-> region.setExpanded(false));
    assertFalse(region.isExpanded());
  }

  public void testEditingNearInlayInBulkMode() {
    initText("a<caret>bc");
    addInlay(1);
    runWriteCommand(()-> DocumentUtil.executeInBulk(myEditor.getDocument(), true,
                                                    ()-> myEditor.getDocument().insertString(1, " ")));
    checkResultByText("a<caret> bc");
    assertTrue(myEditor.getInlayModel().hasInlineElementAt(1));
  }

  public void testCoordinateConversionsAroundSurrogatePair() {
    initText("a" + SURROGATE_PAIR + "b");
    assertEquals(new LogicalPosition(0, 2), myEditor.offsetToLogicalPosition(3));
    assertEquals(3, myEditor.logicalPositionToOffset(new LogicalPosition(0, 2)));
    assertEquals(new VisualPosition(0, 2), myEditor.logicalToVisualPosition(new LogicalPosition(0, 2)));
    assertEquals(new LogicalPosition(0, 2), myEditor.visualToLogicalPosition(new VisualPosition(0, 2)));
  }

  public void testCreationOfSurrogatePairByMergingDividedParts() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> {
      myEditor.getDocument().setText(HIGH_SURROGATE + " " + LOW_SURROGATE);
      myEditor.getDocument().deleteString(1, 2);
    });
    checkResultByText(SURROGATE_PAIR);
    assertEquals(new LogicalPosition(0, 1), myEditor.offsetToLogicalPosition(2));
  }

  public void testCaretDoesntGetInsideSurrogatePair() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> myEditor.getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    myEditor.getCaretModel().moveToOffset(2);
    runWriteCommand(() -> ((DocumentEx)myEditor.getDocument()).moveText(2, 3, 1));
    assertFalse(DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), myEditor.getCaretModel().getOffset()));
  }

  public void testFoldingBoundaryDoesntGetInsideSurrogatePair() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> myEditor.getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
    addFoldRegion(2, 3, "...");
    runWriteCommand(() -> ((DocumentEx)myEditor.getDocument()).moveText(2, 3, 1));
    FoldRegion[] foldRegions = myEditor.getFoldingModel().getAllFoldRegions();
    assertFalse(foldRegions.length > 0 && DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), foldRegions[0].getStartOffset()));
  }

  public void testRemovalOfNonExpandingFoldingRegion() {
    initText("a\nb");
    FoldingModelEx model = (FoldingModelEx)myEditor.getFoldingModel();
    Ref<FoldRegion> regionRef = new Ref<>();
    runFoldingOperation(() -> {
      FoldRegion region = model.createFoldRegion(0, 3, "...", null, true);
      assertNotNull(region);
      assertTrue(region.isValid());
      region.setExpanded(false);
      regionRef.set(region);
    });
    runFoldingOperation(() -> model.removeFoldRegion(regionRef.get()));
    assertEquals(new VisualPosition(1, 1), myEditor.offsetToVisualPosition(3));
  }
  
  public void testPasteWithStickySelection() {
    initText("<selection>abc<caret></selection>\ndef");
    copy();
    down();
    home();
    ((EditorEx)myEditor).setStickySelection(true);
    right();
    right();
    right();
    paste();
    checkResultByText("abc\nabc<caret>");
  }
  
  public void testPasteInVirtualSpaceWhenSelectionExists() {
    initText("foo");
    myEditor.getSettings().setVirtualSpace(true);
    mouse().clickAt(0, 10);
    executeAction(IdeActions.ACTION_SELECT_ALL);
    CopyPasteManager.getInstance().setContents(new StringSelection("bar"));
    paste();
    checkResultByText("bar<caret>");
  }

  public void testMoveTextNearInlayAtBrokenSurrogates() {
    initText(""); // Cannot set up text with singular surrogate characters directly
    runWriteCommand(() -> myEditor.getDocument().setText(LOW_SURROGATE + HIGH_SURROGATE));
    Inlay inlay = addInlay(2);
    FoldRegion region = addCollapsedFoldRegion(1, 2, "...");
    
    runWriteCommand(() -> ((DocumentEx)myEditor.getDocument()).moveText(1, 2, 0));
    
    assertFalse(inlay.isValid() && inlay.getOffset() == 1);
    assertFalse(region.isValid() && (region.getStartOffset() == 1 || region.getEndOffset() == 1));
  }
}
