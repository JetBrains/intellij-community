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

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.EditorTestUtil;

import javax.swing.*;
import java.awt.*;

public class EditorImplTest extends AbstractEditorTest {
  public void testPositionCalculationForZeroWidthChars() throws Exception {
    initText("some\u2044text");
    VisualPosition pos = new VisualPosition(0, 6);
    VisualPosition recalculatedPos = myEditor.xyToVisualPosition(myEditor.visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }

  public void testPositionCalculationOnEmptyLine() throws Exception {
    initText("text with\n" +
         "\n" +
         "empty line");
    VisualPosition pos = new VisualPosition(1, 0);
    VisualPosition recalculatedPos = myEditor.xyToVisualPosition(myEditor.visualPositionToXY(pos));
    assertEquals(pos, recalculatedPos);
  }

  public void testShiftDragAddsToSelection() throws Exception {
    initText("A quick brown fox");
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000); // enable drag testing
    mouse().clickAt(0, 1);
    mouse().shift().clickAt(0, 2).dragTo(0, 3).release();
    checkResultByText("A<selection> q<caret></selection>uick brown fox");
  }

  public void testSoftWrapsRecalculationInASpecificCase() throws Exception {
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

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        Document document = myEditor.getDocument();
        for (int i = document.getLineCount() - 1; i >= 0; i--) {
          document.insertString(document.getLineStartOffset(i), "//");
        }
      }
    }.execute().throwException();


    verifySoftWrapPositions(58, 93);
  }

  public void testCorrectVisibleLineCountCalculation() throws Exception {
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

  public void testInsertingFirstTab() throws Exception {
    initText(" <caret>space-indented line");
    configureSoftWraps(100);
    myEditor.getSettings().setUseTabCharacter(true);

    executeAction(IdeActions.ACTION_EDITOR_TAB);
    checkResultByText(" \t<caret>space-indented line");
  }

  public void testNoExceptionDuringBulkModeDocumentUpdate() throws Exception {
    initText("something");
    DocumentEx document = (DocumentEx)myEditor.getDocument();
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        document.setInBulkUpdate(true);
        try {
          document.setText("something\telse");
        }
        finally {
          document.setInBulkUpdate(false);
        }
      }
    }.execute().throwException();

    checkResultByText("something\telse");
  }

  public void testPositionCalculationForOneCharacterFolds() throws Exception {
    initText("something");
    addCollapsedFoldRegion(1, 2, "...");
    addCollapsedFoldRegion(3, 4, "...");

    assertEquals(new VisualPosition(0, 5), myEditor.logicalToVisualPosition(new LogicalPosition(0, 3)));
  }
  
  public void testNavigationIntoFoldedRegionWithSoftWrapsEnabled() throws Exception {
    initText("something");
    addCollapsedFoldRegion(4, 8, "...");
    configureSoftWraps(1000);
    
    myEditor.getCaretModel().moveToOffset(5);
    
    assertEquals(new VisualPosition(0, 5), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testPositionCalculationForMultilineFoldingWithEmptyPlaceholder() throws Exception {
    initText("line1\n" +
             "line2\n" +
             "line3\n" +
             "line4");
    addCollapsedFoldRegion(6, 17, "");
    configureSoftWraps(1000);
    
    assertEquals(new LogicalPosition(3, 0), myEditor.visualToLogicalPosition(new VisualPosition(2, 0)));
  }
  
  public void testNavigationInsideNonNormalizedLineTerminator() throws Exception {
    initText("");
    ((DocumentImpl)myEditor.getDocument()).setAcceptSlashR(true);
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        myEditor.getDocument().insertString(0, "abc\r\ndef");
      }
    }.execute().throwException();


    myEditor.getCaretModel().moveToOffset(4);
    
    assertEquals(new LogicalPosition(0, 3), myEditor.getCaretModel().getLogicalPosition());
    assertEquals(new VisualPosition(0, 3), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testSoftWrapModeUpdateDuringBulkModeChange() throws Exception {
    initText("long long line<caret>");
    configureSoftWraps(12);
    DocumentEx document = (DocumentEx)myEditor.getDocument();
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        document.setInBulkUpdate(true);
        document.replaceString(4, 5, "-");
        document.setInBulkUpdate(false);
      }
    }.execute().throwException();

    assertEquals(new VisualPosition(1, 5), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testSuccessiveBulkModeOperations() throws Exception {
    initText("some text");
    DocumentEx document = (DocumentEx)myEditor.getDocument();

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        document.setInBulkUpdate(true);
        document.replaceString(4, 5, "-");
        document.setInBulkUpdate(false);

        myEditor.getCaretModel().moveToOffset(9);

        document.setInBulkUpdate(true);
        document.replaceString(4, 5, "+");
        document.setInBulkUpdate(false);
      }
    }.execute().throwException();


    checkResultByText("some+text<caret>");
  }
  
  public void testCorrectCaretPositionRestorationAfterMultipleFoldRegionsChange() throws Exception {
    initText("so<caret>me long text");
    final FoldRegion innerRegion = addCollapsedFoldRegion(0, 4, "...");
    final FoldRegion outerRegion = addCollapsedFoldRegion(0, 9, "...");

    myEditor.getFoldingModel().runBatchFoldingOperation(() -> {
      myEditor.getFoldingModel().removeFoldRegion(outerRegion);
      myEditor.getFoldingModel().removeFoldRegion(innerRegion);
    });

    checkResultByText("so<caret>me long text");
  }
  
  public void testEditorSizeCalculationOnOpening() throws Exception {
    initText("a\nbbb\nccccc");
    myEditor.getSettings().setAdditionalColumnsCount(0);
    myEditor.getSettings().setAdditionalLinesCount(0);
    assertEquals(new Dimension(50, 30), myEditor.getContentComponent().getPreferredSize());
  }
  
  public void testCollapsingRegionContainingSoftWrap() throws Exception {
    initText("abcdef abcdef");
    configureSoftWraps(10);
    addCollapsedFoldRegion(0, 13, "...");
    assertEquals(new VisualPosition(0, 3), myEditor.offsetToVisualPosition(13));
  }
  
  public void testSizeRecalculationOnTurningSoftWrapsOff() throws Exception {
    initText("abc def");
    myEditor.getSettings().setAdditionalColumnsCount(0);
    myEditor.getSettings().setAdditionalLinesCount(0);
    configureSoftWraps(4);
    myEditor.getSettings().setUseSoftWraps(false);
    assertEquals(new Dimension(70, 10), myEditor.getContentComponent().getPreferredSize());
  }
  
  public void testUpdatingCaretPositionAfterBulkMode() throws Exception {
    initText("a<caret>bc");
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        DocumentEx document = (DocumentEx)myEditor.getDocument();
        document.setInBulkUpdate(true);
        document.insertString(0, "\n "); // we're changing number of visual lines, and invalidating text layout for caret line
        document.setInBulkUpdate(false);
      }
    }.execute().throwException();

    checkResultByText("\n a<caret>bc");
  }
  
  public void testAllowCaretPositioningInsideSoftWrapOnlyIfVirtualSpaceIsEnabled() throws Exception {
    initText("abcdef abcdef");
    configureSoftWraps(10);
    mouse().clickAt(0, 10);
    assertEquals(new VisualPosition(0, 7), myEditor.getCaretModel().getVisualPosition());
  }
  
  public void testXYToVisualPositionWhenPrefixIsSet() throws Exception {
    initText("abc");
    configureSoftWraps(1000); // to make sure soft wrap character width is properly mocked
    ((EditorEx)myEditor).setPrefixTextAndAttributes(">", new TextAttributes());
    assertEquals(new VisualPosition(0, 0), myEditor.xyToVisualPosition(new Point(1, 0)));
  }
  
  public void testLogicalPositionCacheInvalidationAfterLineDeletion() throws Exception {
    initText("<caret>\n\t");
    delete();
    assertEquals(new LogicalPosition(0, 4), myEditor.offsetToLogicalPosition(1));
  }
  
  public void testSoftWrapsUpdateAfterEditorWasHidden() throws Exception {
    initText("long long line");
    configureSoftWraps(6);
    verifySoftWrapPositions(5, 10);

    JViewport viewport = ((EditorEx)myEditor).getScrollPane().getViewport();
    Dimension normalSize = viewport.getExtentSize();
    viewport.setExtentSize(new Dimension(0, 0));
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        myEditor.getDocument().deleteString(5, 14);
      }
    }.execute().throwException();

    viewport.setExtentSize(normalSize);
    
    verifySoftWrapPositions();
  }
}
