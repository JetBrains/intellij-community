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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.BidiTextDirection;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.testFramework.TestFileType;

import java.io.IOException;

public class EditorRtlTest extends AbstractRtlTest {
  public void testPositionCalculations() throws IOException {
    prepareText("LLRR");
    
    checkOffsetConversions(0, lB(0), vL(0), vR(0), xy(0));
    checkOffsetConversions(1, lB(1), vL(1), vR(1), xy(10));
    checkOffsetConversions(2, lB(2), vL(2), vL(4), xy(20), xy(40));
    checkOffsetConversions(3, lB(3), vR(3), vL(3), xy(30));
    checkOffsetConversions(4, lB(4), vR(2), vR(4), xy(20), xy(40));
    
    checkLPConversions(0, 0, vL(0), vR(0)); 
    checkLPConversions(1, 1, vL(1), vR(1)); 
    checkLPConversions(2, 2, vL(2), vL(4)); 
    checkLPConversions(3, 3, vR(3), vL(3)); 
    checkLPConversions(4, 4, vR(2), vR(4)); 
    checkLPConversions(5, 4, vL(5), vR(5)); 
    checkLPConversions(lB(1, 0), 4, vL(1, 0));
    checkLPConversions(lF(1, 0), 4, vR(1, 0));
    
    checkVPConversions(vL(0), lB(0), xy(0));
    checkVPConversions(vR(0), lF(0), xy(0));
    checkVPConversions(vL(1), lB(1), xy(10));
    checkVPConversions(vR(1), lF(1), xy(10));
    checkVPConversions(vL(2), lB(2), xy(20));
    checkVPConversions(vR(2), lB(4), xy(20));
    checkVPConversions(vL(3), lF(3), xy(30));
    checkVPConversions(vR(3), lB(3), xy(30));
    checkVPConversions(vL(4), lF(2), xy(40));
    checkVPConversions(vR(4), lF(4), xy(40));
    checkVPConversions(vL(5), lF(5), xy(50));
    checkVPConversions(vR(5), lF(5), xy(50));
    checkVPConversions(vL(1, 0), lB(1, 0), xy(0, 10));
    checkVPConversions(vR(1, 0), lF(1, 0), xy(0, 10));
    
    checkXYConversion(xy(0),  vL(0));
    checkXYConversion(xy(12), vR(1));
    checkXYConversion(xy(19), vL(2));
    checkXYConversion(xy(21), vR(2));
    checkXYConversion(xy(27), vL(3));
    checkXYConversion(xy(39), vL(4));
    checkXYConversion(xy(41), vR(4));
    checkXYConversion(xy(51), vR(5));
  }
  
  public void testNumberInsideRtlText() throws IOException {
    prepareText("RR12RR");

    checkOffsetConversions(0, lB(0), vL(0), vL(6), xy(0), xy(60));
    checkOffsetConversions(1, lB(1), vR(5), vL(5), xy(50));
    checkOffsetConversions(2, lB(2), vR(4), vR(2), xy(40), xy(20));
    checkOffsetConversions(3, lB(3), vL(3), vR(3), xy(30));
    checkOffsetConversions(4, lB(4), vL(4), vL(2), xy(40), xy(20));
    checkOffsetConversions(5, lB(5), vR(1), vL(1), xy(10));
    checkOffsetConversions(6, lB(6), vR(0), vR(6), xy(0), xy(60));
    
    checkLPConversions(0, 0, vL(0), vL(6));
    checkLPConversions(1, 1, vR(5), vL(5));
    checkLPConversions(2, 2, vR(4), vR(2));
    checkLPConversions(3, 3, vL(3), vR(3));
    checkLPConversions(4, 4, vL(4), vL(2));
    checkLPConversions(5, 5, vR(1), vL(1));
    checkLPConversions(6, 6, vR(0), vR(6));

    checkVPConversions(vL(0), lB(0), xy(0));
    checkVPConversions(vR(0), lB(6), xy(0));
    checkVPConversions(vL(1), lF(5), xy(10));
    checkVPConversions(vR(1), lB(5), xy(10));
    checkVPConversions(vL(2), lF(4), xy(20));
    checkVPConversions(vR(2), lF(2), xy(20));
    checkVPConversions(vL(3), lB(3), xy(30));
    checkVPConversions(vR(3), lF(3), xy(30));
    checkVPConversions(vL(4), lB(4), xy(40));
    checkVPConversions(vR(4), lB(2), xy(40));
    checkVPConversions(vL(5), lF(1), xy(50));
    checkVPConversions(vR(5), lB(1), xy(50));
    checkVPConversions(vL(6), lF(0), xy(60));
    checkVPConversions(vR(6), lF(6), xy(60));

    checkXYConversion(xy(1), vR(0));
    checkXYConversion(xy(9), vL(1));
    checkXYConversion(xy(19), vL(2));
    checkXYConversion(xy(21), vR(2));
    checkXYConversion(xy(30), vL(3));
    checkXYConversion(xy(39), vL(4));
    checkXYConversion(xy(41), vR(4));
    checkXYConversion(xy(50), vL(5));
    checkXYConversion(xy(59), vL(6));
    checkXYConversion(xy(61), vR(6));
  }
  
  public void testFolding() throws IOException {
    prepareText("RRRRRR");
    addCollapsedFoldRegion(2, 4, "...");
    
    checkOffsetConversions(0, lB(0), vL(0), vL(2), xy(0), xy(20));
    checkOffsetConversions(1, lB(1), vR(1), vL(1), xy(10));
    checkOffsetConversions(2, lB(2), vR(0), vR(2), xy(0), xy(20));
    checkOffsetConversions(3, lB(3), vR(2), vR(2), xy(20));
    checkOffsetConversions(4, lB(4), vL(5), vL(7), xy(50), xy(70));
    checkOffsetConversions(5, lB(5), vR(6), vL(6), xy(60));
    checkOffsetConversions(6, lB(6), vR(5), vR(7), xy(50), xy(70));
    
    checkLPConversions(0, 0, vL(0), vL(2));
    checkLPConversions(1, 1, vR(1), vL(1));
    checkLPConversions(2, 2, vR(0), vR(2));
    checkLPConversions(3, 3, vR(2), vR(2));
    checkLPConversions(4, 4, vL(5), vL(7));
    checkLPConversions(5, 5, vR(6), vL(6));
    checkLPConversions(6, 6, vR(5), vR(7));
    checkLPConversions(7, 6, vL(8), vR(8));
    
    checkVPConversions(vL(0), lB(0), xy(0));
    checkVPConversions(vR(0), lB(2), xy(0));
    checkVPConversions(vL(1), lF(1), xy(10));
    checkVPConversions(vR(1), lB(1), xy(10));
    checkVPConversions(vL(2), lF(0), xy(20));
    checkVPConversions(vR(2), lF(2), xy(20));
    checkVPConversions(vL(3), lF(2), xy(30));
    checkVPConversions(vR(3), lF(2), xy(30));
    checkVPConversions(vL(4), lF(2), xy(40));
    checkVPConversions(vR(4), lF(2), xy(40));
    checkVPConversions(vL(5), lB(4), xy(50));
    checkVPConversions(vR(5), lB(6), xy(50));
    checkVPConversions(vL(6), lF(5), xy(60));
    checkVPConversions(vR(6), lB(5), xy(60));
    checkVPConversions(vL(7), lF(4), xy(70));
    checkVPConversions(vR(7), lF(6), xy(70));
    checkVPConversions(vL(8), lF(7), xy(80));
    checkVPConversions(vR(8), lF(7), xy(80));
    
    checkXYConversion(xy(1), vR(0));
    checkXYConversion(xy(9), vL(1));
    checkXYConversion(xy(19), vL(2));
    checkXYConversion(xy(21), vR(2));
    checkXYConversion(xy(30), vL(3));
    checkXYConversion(xy(39), vL(4));
    checkXYConversion(xy(41), vR(4));
    checkXYConversion(xy(50), vL(5));
    checkXYConversion(xy(59), vL(6));
    checkXYConversion(xy(69), vL(7));
    checkXYConversion(xy(71), vR(7));
    checkXYConversion(xy(81), vR(8));
  }
  
  public void testFoldingWhenLineContainsSeveralFragments() throws IOException {
    prepareText("R R");
    addCollapsedFoldRegion(1, 2, "...");
    
    checkOffsetConversions(0, lB(0), vL(0), vL(1), xy(0), xy(10));
    checkOffsetConversions(1, lB(1), vR(0), vR(1), xy(0), xy(10));
    checkOffsetConversions(2, lB(2), vL(4), vL(5), xy(40), xy(50));
    checkOffsetConversions(3, lB(3), vR(4), vR(5), xy(40), xy(50));
  }
  
  public void testFoldingInInnerBidiRun() throws IOException {
    prepareText("LRRL");
    addCollapsedFoldRegion(1, 2, "...");
    
    checkLPConversions(0, 0, vL(0), vR(0));
    checkLPConversions(1, 1, vL(1), vR(1));
    checkLPConversions(2, 2, vL(4), vL(5));
    checkLPConversions(3, 3, vR(4), vR(5));
    checkLPConversions(4, 4, vL(6), vR(6));
  }

  public void testPositionCalculationsWithSoftWraps() throws Exception {
    prepareText("RR RR");
    configureSoftWraps(3);

    checkOffsetConversions(0, lB(0), vL(0), vL(3), xy(0), xy(30));
    checkOffsetConversions(1, lB(1), vR(2), vL(2), xy(20));
    checkOffsetConversions(2, lB(2), vR(1), vL(1), xy(10));
    checkOffsetConversions(3, lB(3), vL(1, 1), vL(1, 3),  xy(10, 10), xy(30, 10));
    checkOffsetConversions(4, lB(4), vR(1, 2), vL(1, 2),  xy(20, 10));
    checkOffsetConversions(5, lB(5), vR(1, 1), vR(1, 3),  xy(10, 10), xy(30, 10));
    checkOffsetConversions(6, lB(5), vR(1, 1), vR(1, 3),  xy(10, 10), xy(30, 10));

    checkLPConversions(0, 0, vL(0), vL(3));
    checkLPConversions(1, 1, vR(2), vL(2));
    checkLPConversions(2, 2, vR(1), vL(1));
    checkLPConversions(3, 3, vL(1, 1), vL(1, 3));
    checkLPConversions(4, 4, vR(1, 2), vL(1, 2));
    checkLPConversions(5, 5, vR(1, 1), vR(1, 3));
    checkLPConversions(6, 5, vL(1, 4), vR(1, 4));
    checkLPConversions(lB(1, 0), 5, vL(2, 0));
    checkLPConversions(lF(1, 0), 5, vR(2, 0));
    checkLPConversions(lB(1, 1), 5, vL(2, 1));
    checkLPConversions(lF(1, 1), 5, vR(2, 1));

    checkVPConversions(0, lB(0), lB(3), xy(0));
    checkVPConversions(1, lF(2), lB(2), xy(10));
    checkVPConversions(2, lF(1), lB(1), xy(20));
    checkVPConversions(3, lF(0), lF(3), xy(30));
    checkVPConversions(4, lF(3), lF(3), xy(40));
    checkVPConversions(5, lF(3), lF(3), xy(50));
    checkVPConversions(vL(1, 0), lB(3), xy(0, 10));
    checkVPConversions(vR(1, 0), lB(3), xy(0, 10));
    checkVPConversions(vL(1, 1), lB(3), xy(10, 10));
    checkVPConversions(vR(1, 1), lB(5), xy(10, 10));
    checkVPConversions(vL(1, 2), lF(4), xy(20, 10));
    checkVPConversions(vR(1, 2), lB(4), xy(20, 10));
    checkVPConversions(vL(1, 3), lF(3), xy(30, 10));
    checkVPConversions(vR(1, 3), lF(5), xy(30, 10));
    checkVPConversions(vL(1, 4), lF(6), xy(40, 10));
    checkVPConversions(vR(1, 4), lF(6), xy(40, 10));

    checkXYConversion(xy(0), vL(0));
    checkXYConversion(xy(2), vR(0));
    checkXYConversion(xy(9), vL(1));
    checkXYConversion(xy(13), vR(1));
    checkXYConversion(xy(18), vL(2));
    checkXYConversion(xy(24), vR(2));
    checkXYConversion(xy(30), vL(3));
    checkXYConversion(xy(31), vR(3));
    checkXYConversion(xy(39), vL(4));
    checkXYConversion(xy(42), vR(4));
    checkXYConversion(xy(47), vL(5));
    checkXYConversion(xy(54), vR(5));
    checkXYConversion(xy(0, 10), vL(1, 0));
    checkXYConversion(xy(2, 10), vR(1, 0));
    checkXYConversion(xy(9, 10), vL(1, 1));
    checkXYConversion(xy(11, 10), vR(1, 1));
    checkXYConversion(xy(18, 10), vL(1, 2));
    checkXYConversion(xy(23, 10), vR(1, 2));
    checkXYConversion(xy(30, 10), vL(1, 3));
    checkXYConversion(xy(34, 10), vR(1, 3));
    checkXYConversion(xy(37, 10), vL(1, 4));
    checkXYConversion(xy(41, 10), vR(1, 4));
  }
  
  public void testSelectingRtlLineByDraggingMouseFromLeftToRight() throws IOException {
    prepareText("R");
    setEditorVisibleSize(1000, 1000);
    mouse().pressAtXY(0, 5).dragToXY(15, 5).release();
    
    checkResult("<selection>R</selection>");
  }
  
  public void testSelectingRtlLineByDraggingMouseFromRightToLeft() throws IOException {
    prepareText("R");
    setEditorVisibleSize(1000, 1000);
    mouse().pressAtXY(15, 5).dragToXY(0, 5).release();

    checkResult("<selection>R</selection>");
  }
  
  public void testMovingCaretToLogicalLineEnd() throws IOException {
    prepareText("R");
    myEditor.getCaretModel().moveToLogicalPosition(lF(1));
    assertVisualPositionsEqual("Wrong visual position", vR(1), myEditor.getCaretModel().getVisualPosition());
  }

  public void testMovingCaretToVisualLineEnd() throws IOException {
    prepareText("R");
    myEditor.getCaretModel().moveToVisualPosition(vR(1));
    assertLogicalPositionsEqual("Wrong logical position", lF(1), myEditor.getCaretModel().getLogicalPosition());
  }

  public void testNavigationWithArrowKeys() throws Exception {
    prepareText("LLRRLL\nLLRRLL");
    assertCaretPosition(vL(0));
    right();
    assertCaretPosition(vL(1));
    right();
    assertCaretPosition(vL(2));
    right();
    assertCaretPosition(vR(2));
    right();
    assertCaretPosition(vL(3));
    right();
    assertCaretPosition(vL(4));
    down();
    assertCaretPosition(vL(1, 4));
    left();
    assertCaretPosition(vR(1, 3));
    left();
    assertCaretPosition(vR(1, 2));
    up();
    assertCaretPosition(vR(2));
  }
  
  public void testMovingIntoVirtualSpace() throws Exception {
    prepareText("R");
    myEditor.getSettings().setVirtualSpace(true);
    assertVisualCaretLocation(0, false);
    right();
    assertVisualCaretLocation(0, true);
    right();
    assertVisualCaretLocation(1, true);
    right();
    assertVisualCaretLocation(1, false);
    right();
    assertVisualCaretLocation(2, false);
    right();
    assertVisualCaretLocation(3, false);
  }
  
  public void testMovingThroughFoldedRegion() throws Exception {
    prepareText("RRR");
    addCollapsedFoldRegion(1, 2, "..");
    assertVisualCaretLocation(0, false);
    right();
    assertVisualCaretLocation(0, true);
    right();
    assertVisualCaretLocation(1, true);
    right();
    assertVisualCaretLocation(1, true);
    right();
    assertVisualCaretLocation(2, true);
    right();
    assertVisualCaretLocation(3, true);
    right();
    assertVisualCaretLocation(3, true);
    right();
    assertVisualCaretLocation(4, true);
  }
  
  public void testMovingCaretWhenSelectionExists() throws Exception {
    prepareText("RRR");
    right();
    right();
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResult("R<selection>R</selection>R");
    right();
    assertVisualCaretLocation(2, true);
  }
  
  public void testMovingCaretWhenSelectionStartsOnDirectionBoundary() throws Exception {
    prepareText("LR");
    right();
    right();
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    right();
    assertVisualCaretLocation(2, true);
  }
  
  public void testMoveCaretWithSelection() throws Exception {
    prepareText("LR");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResult("<selection>LR</selection>");
  }
  
  public void testNextPrevWord() throws Exception {
    prepareText("LL RR RR LL");
    nextWord();
    assertVisualCaretLocation(3, false);
    nextWord();
    assertVisualCaretLocation(3, true);
    nextWord();
    assertVisualCaretLocation(5, true);
    nextWord();
    assertVisualCaretLocation(8, true);
    nextWord();
    assertVisualCaretLocation(8, false);
    nextWord();
    assertVisualCaretLocation(9, false);
    nextWord();
    assertVisualCaretLocation(11, false);
    previousWord();
    assertVisualCaretLocation(9, false);
    previousWord();
    assertVisualCaretLocation(8, false);
    previousWord();
    assertVisualCaretLocation(8, true);
    previousWord();
    assertVisualCaretLocation(5, true);
    previousWord();
    assertVisualCaretLocation(3, true);
    previousWord();
    assertVisualCaretLocation(3, false);
    previousWord();
    assertVisualCaretLocation(0, false);
  }

  public void testNextPrevWordWithSelection() throws Exception {
    prepareText("LL RR RR LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL </selection>RR RR LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL RR RR</selection> LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL RR </selection>RR LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL </selection>RR RR LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL RR RR</selection> LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL RR RR </selection>LL");
    moveCaretToNextWordWithSelection();
    checkResult("<selection>LL RR RR LL</selection>");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection>LL RR RR </selection>LL");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection>LL RR RR</selection> LL");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection>LL </selection>RR RR LL");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection>LL RR </selection>RR LL");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection>LL RR RR</selection> LL");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection>LL </selection>RR RR LL");
    moveCaretToPreviousWordWithSelection();
    checkResult("<selection></selection>LL RR RR LL");
  }
  
  public void testHomeEnd() throws Exception {
    prepareText("RR");
    myEditor.getCaretModel().moveToOffset(1);
    home();
    assertVisualCaretLocation(0, false);
    end();
    assertVisualCaretLocation(2, false);
  }
  
  public void testHomeEndWithSelection() throws Exception {
    prepareText("RR");
    myEditor.getCaretModel().moveToOffset(1);
    homeWithSelection();
    assertVisualCaretLocation(0, false);
    checkResult("<selection>R</selection>R");
    endWithSelection();
    assertVisualCaretLocation(2, false);
    checkResult("R<selection>R</selection>");
  }
  
  public void testTextStartEnd() throws Exception {
    prepareText("RR");
    myEditor.getCaretModel().moveToOffset(1);
    executeAction(IdeActions.ACTION_EDITOR_TEXT_START);
    assertVisualCaretLocation(0, false);
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END);
    assertVisualCaretLocation(2, false);
  }
  
  public void testTextStartEndWithSelection() throws Exception {
    prepareText("RR");
    myEditor.getCaretModel().moveToOffset(1);
    executeAction(IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION);
    assertVisualCaretLocation(0, false);
    checkResult("<selection>R</selection>R");
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION);
    assertVisualCaretLocation(2, false);
    checkResult("R<selection>R</selection>");
  }

  public void testUsingLexerForBidiLayout() {
    prepare("class Foo {\n  int<caret> R = 1;\n}", TestFileType.JAVA);
    right();
    right();
    
    checkResult("class Foo {\n  int R<caret> = 1;\n}");
  }
  
  public void testJavadocTokensAreMergedForBidiLayoutPurposes() {
    prepare("<caret>/**R R*/ class Foo {}", TestFileType.JAVA);
    for (int i = 0; i < 4; i++) {
      right();
    }

    checkResult("/**R R<caret>*/ class Foo {}");
  }
  
  public void testXmlTextIsLaidOutCorrectly() {
    prepare("<caret><L>R R</L>", TestFileType.XML);
    for (int i = 0; i < 4; i++) {
      right();
    }

    checkResult("<L>R R<caret></L>");
  }
  
  public void testMovingThroughBoundaryBetweenRunsWithNonadjacentLevels() throws Exception {
    prepareText("R=1");
    right();
    assertVisualCaretLocation(0, false);
    assertEquals(2, myEditor.getCaretModel().getOffset());
    right();
    assertVisualCaretLocation(1, false);
    assertEquals(3, myEditor.getCaretModel().getOffset());
    right();
    assertVisualCaretLocation(1, true);
    assertEquals(2, myEditor.getCaretModel().getOffset());
  }
  
  public void testMovingCaretThroughSoftWrap() throws Exception {
    prepareText("RR RR");
    configureSoftWraps(3);
    
    right();
    assertVisualCaretLocation(0, true);
    right();
    assertVisualCaretLocation(1, true);
    right();
    assertVisualCaretLocation(2, true);
    right();
    assertVisualCaretLocation(3, true);
    right();
    assertVisualCaretLocation(3, false);
    right();
    assertVisualCaretLocation(1, 1, false);
    right();
    assertVisualCaretLocation(1, 1, true);
    right();
    assertVisualCaretLocation(1, 2, true);
    right();
    assertVisualCaretLocation(1, 3, true);
  }
  
  public void testLineGeneralDirectionAutodetection() throws Exception {
    prepareText("<caret>RLR");
    right();
    checkResult("RLR<caret>");
  }
  
  public void testTabInsideRtlText() throws Exception {
    prepareText("<caret>R\tRR");
    right();
    checkResult("R<caret>\tRR");
    right();
    checkResult("<caret>R\tRR");
    right();
    checkResult("R<caret>\tRR");
    right();
    checkResult("R\t<caret>RR");
    right();
    checkResult("R\tRR<caret>");
    right();
    checkResult("R\tR<caret>R");
    right();
    checkResult("R\t<caret>RR");
    right();
    checkResult("R\tRR<caret>");
  }
  
  public void testEscapedBackslashInStringLiteral() {
    prepare("class C {\n  String s = \"R\\<caret>\\R\";\n}", TestFileType.JAVA);
    right();
    right();
    checkResult("class C {\n  String s = \"<caret>R\\\\R\";\n}");
  }

  public void testRtlLayoutPersistsAfterEditing() {
    prepare("<caret>\nRR", TestFileType.TEXT);
    delete();
    checkResult("<caret>RR");
    assertTrue(myEditor.getCaretModel().getPrimaryCaret().isAtBidiRunBoundary());
  }

  public void testTokenOrderIsAlwaysLtr() {
    BidiTextDirection savedValue = EditorSettingsExternalizable.getInstance().getBidiTextDirection();
    try {
      EditorSettingsExternalizable.getInstance().setBidiTextDirection(BidiTextDirection.RTL);
      prepare("<a>R</a>", TestFileType.XML);
      right();
      checkResult("<<caret>a>R</a>");
    }
    finally {
      EditorSettingsExternalizable.getInstance().setBidiTextDirection(savedValue);
    }
  }

  public void testLineCommentLayout() {
    prepare("<caret>// R", TestFileType.JAVA);
    right();
    checkResult("/<caret>/ R");
  }

  public void testIndentIsTreatedSeparately() {
    checkBidiRunBoundaries(" |R", "txt");
  }
}
