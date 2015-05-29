/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.util.registry.Registry;

import java.awt.*;
import java.io.IOException;

/**
 * To simplify the representation of input text, 'r' character in these tests represents an RTL character.
 */
public class EditorRtlTest extends AbstractEditorTest {
  private static final char RTL_CHAR_REPRESENTATION = 'r';
  private static final char RTL_CHAR = '\u05d0'; // Hebrew 'aleph' letter

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("editor.new.rendering").setValue(true);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    Registry.get("editor.new.rendering").setValue(false);
  }

  public void testPositionCalculations() throws IOException {
    init("llrr");
    
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
    checkLPConversions(l(1, 0, false), 4, v(1, 0, false)); 
    checkLPConversions(l(1, 0, true), 4, v(1, 0, true)); 
    
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
    checkVPConversions(v(1, 0, false), l(1, 0, false), xy(0, 10));
    checkVPConversions(v(1, 0, true), l(1, 0, true), xy(0, 10));
    
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
    init("rr12rr");

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
    init("rrrrrr");
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
    init("r r");
    addCollapsedFoldRegion(1, 2, "...");
    
    checkOffsetConversions(0, lB(0), vL(0), vL(1), xy(0), xy(10));
    checkOffsetConversions(1, lB(1), vR(0), vR(1), xy(0), xy(10));
    checkOffsetConversions(2, lB(2), vL(4), vL(5), xy(40), xy(50));
    checkOffsetConversions(3, lB(3), vR(4), vR(5), xy(40), xy(50));
  }
  
  public void testFoldingInInnerBidiRun() throws IOException {
    init("lrrl");
    addCollapsedFoldRegion(1, 2, "...");
    
    checkLPConversions(0, 0, vL(0), vR(0));
    checkLPConversions(1, 1, vL(1), vR(1));
    checkLPConversions(2, 2, vL(4), vL(5));
    checkLPConversions(3, 3, vR(4), vR(5));
    checkLPConversions(4, 4, vL(6), vR(6));
  }
  
  public void testSelectingRtlLineByDraggingMouseFromLeftToRight() throws IOException {
    init("r");
    setEditorVisibleSize(1000, 1000);
    mouse().pressAtXY(0, 5).dragToXY(15, 5).release();
    
    assertEquals(0, myEditor.getSelectionModel().getSelectionStart());
    assertEquals(1, myEditor.getSelectionModel().getSelectionEnd());
  }
  
  public void testSelectingRtlLineByDraggingMouseFromRightToLeft() throws IOException {
    init("r");
    setEditorVisibleSize(1000, 1000);
    mouse().pressAtXY(15, 5).dragToXY(0, 5).release();
    
    assertEquals(0, myEditor.getSelectionModel().getSelectionStart());
    assertEquals(1, myEditor.getSelectionModel().getSelectionEnd());
  }
  
  public void testMovingCaretToLogicalLineEnd() throws IOException {
    init("r");
    myEditor.getCaretModel().moveToLogicalPosition(lF(1));
    assertVisualPositionsEqual("Wrong visual position", vR(1), myEditor.getCaretModel().getVisualPosition());
  }

  public void testMovingCaretToVisualLineEnd() throws IOException {
    init("r");
    myEditor.getCaretModel().moveToVisualPosition(vR(1));
    assertLogicalPositionsEqual("Wrong logical position", lF(1), myEditor.getCaretModel().getLogicalPosition());
  }

  public void testNavigationWithArrowKeys() throws Exception {
    init("llrrll\nllrrll");
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
    assertCaretPosition(v(1, 4, false));
    left();
    assertCaretPosition(v(1, 3, true));
    left();
    assertCaretPosition(v(1, 2, true));
    up();
    assertCaretPosition(vR(2));
  }
  
  public void testMovingIntoVirtualSpace() throws Exception {
    init("r");
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
    init("rrr");
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
    assertVisualCaretLocation(4, true);
  }
  
  public void testMovingCaretWhenSelectionExists() throws Exception {
    init("rrr");
    right();
    right();
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    assertEquals(1, myEditor.getSelectionModel().getSelectionStart());
    assertEquals(2, myEditor.getSelectionModel().getSelectionEnd());
    right();
    assertVisualCaretLocation(2, true);
  }

  private void init(String text) throws IOException {
    initText(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));
  }

  private static void checkOffsetConversions(int offset,
                                             LogicalPosition logicalPosition,
                                             VisualPosition visualPositionTowardsSmallerOffsets,
                                             VisualPosition visualPositionTowardsLargerOffsets,
                                             Point xy) {
    checkOffsetConversions(offset, logicalPosition, visualPositionTowardsSmallerOffsets, visualPositionTowardsLargerOffsets, xy, xy);
  }

  private static void checkOffsetConversions(int offset, 
                                             LogicalPosition logicalPosition, 
                                             VisualPosition visualPositionTowardsSmallerOffsets, 
                                             VisualPosition visualPositionTowardsLargerOffsets, 
                                             Point xyTowardsSmallerOffsets, 
                                             Point xyTowardsLargerOffsets) {
    assertLogicalPositionsEqual("Wrong offset->logicalPosition calculation", logicalPosition, myEditor.offsetToLogicalPosition(offset));
    assertVisualPositionsEqual("Wrong beforeOffset->visualPosition calculation",
                               visualPositionTowardsSmallerOffsets, myEditor.offsetToVisualPosition(offset, false));
    assertEquals("Wrong beforeOffset->visualLine calculation", 
                 visualPositionTowardsSmallerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertVisualPositionsEqual("Wrong afterOffset->visualPosition calculation",
                               visualPositionTowardsLargerOffsets, myEditor.offsetToVisualPosition(offset, true));
    assertEquals("Wrong afterOffset->visualLine calculation", 
                 visualPositionTowardsLargerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertEquals("Wrong beforeOffset->xy calculation", xyTowardsSmallerOffsets, ((EditorImpl)myEditor).offsetToXY(offset, false));
    assertEquals("Wrong afterOffset->xy calculation", xyTowardsLargerOffsets, ((EditorImpl)myEditor).offsetToXY(offset, true));
  }

  private static void checkLPConversions(int logicalColumn, int offset, 
                                         VisualPosition visualPositionForPrecedingLp, VisualPosition visualPositionForSucceedingLp) {
    checkLPConversions(lB(logicalColumn), offset, visualPositionForPrecedingLp);
    checkLPConversions(lF(logicalColumn), offset, visualPositionForSucceedingLp);
    
  }
  
  private static void checkLPConversions(LogicalPosition logicalPosition, int offset, VisualPosition visualPosition) {
    assertEquals("Wrong logicalPosition->offset calculation", offset, myEditor.logicalPositionToOffset(logicalPosition));
    assertVisualPositionsEqual("Wrong beforeLogicalPosition->visualPosition calculation",
                               visualPosition, myEditor.logicalToVisualPosition(logicalPosition));
  }

  private static void checkVPConversions(VisualPosition visualPosition, LogicalPosition logicalPosition, Point xy) {
    assertLogicalPositionsEqual("Wrong beforeVisualPosition->logicalPosition calculation",
                                logicalPosition, myEditor.visualToLogicalPosition(visualPosition));
    assertEquals("Wrong visualPosition->xy calculation", xy, myEditor.visualPositionToXY(visualPosition));
  }
  
  private static void checkXYConversion(Point xy,
                                       VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong xy->visualPosition calculation", visualPosition, myEditor.xyToVisualPosition(xy));
  }

  private static void assertLogicalPositionsEqual(String message, LogicalPosition expectedPosition, LogicalPosition actualPosition) {
    assertEquals(message, expectedPosition, actualPosition);
    assertEquals(message + " (direction flag)", expectedPosition.leansForward, actualPosition.leansForward);
  }

  private static void assertVisualPositionsEqual(String message, VisualPosition expectedPosition, VisualPosition actualPosition) {
    assertEquals(message, expectedPosition, actualPosition);
    assertEquals(message + " (direction flag)", expectedPosition.leansRight, actualPosition.leansRight);
  }

  private static void assertCaretPosition(VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong caret position", visualPosition, myEditor.getCaretModel().getVisualPosition());
  }

  private static void assertVisualCaretLocation(int visualColumn, boolean reversedDirection) {
    assertEquals(1, myEditor.getCaretModel().getCaretCount());
    Caret caret = myEditor.getCaretModel().getPrimaryCaret();
    assertEquals(visualColumn, caret.getVisualPosition().column);
    assertEquals(reversedDirection, caret.isAtRtlLocation());
  }
  
  // logical position leaning backward
  private static LogicalPosition lB(int column) {
    return new LogicalPosition(0, column);
  }
  
  // logical position leaning forward
  private static LogicalPosition lF(int column) {
    return new LogicalPosition(0, column, true);
  }
  
  private static LogicalPosition l(int line, int column, boolean leanTowardsLargerColumns) {
    return new LogicalPosition(line, column, leanTowardsLargerColumns);
  }
 
  // visual position leaning to the left
  private static VisualPosition vL(int column) {
    return new VisualPosition(0, column);
  }

  // visual position leaning to the right
  private static VisualPosition vR(int column) {
    return new VisualPosition(0, column, true);
  }
  
  private static VisualPosition v(int line, int column, boolean leanTowardsLargerColumns) {
    return new VisualPosition(line, column, leanTowardsLargerColumns);
  }
  
  private static Point xy(int x) {
    return new Point(x, 0);
  }
  
  private static Point xy(int x, int y) {
    return new Point(x, y);
  }
}
