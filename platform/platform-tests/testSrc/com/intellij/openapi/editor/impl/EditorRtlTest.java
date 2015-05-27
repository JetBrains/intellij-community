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
    
    checkOffsetConversions(0, lP(0), vL(0), vR(0), xy(0));
    checkOffsetConversions(1, lP(1), vL(1), vR(1), xy(10));
    checkOffsetConversions(2, lP(2), vL(2), vL(4), xy(20), xy(40));
    checkOffsetConversions(3, lP(3), vR(3), vL(3), xy(30));
    checkOffsetConversions(4, lP(4), vR(2), vR(4), xy(20), xy(40));
    
    checkLPConversions(lP(0), 0, vL(0)); 
    checkLPConversions(lS(0), 0, vR(0)); 
    checkLPConversions(lP(1), 1, vL(1)); 
    checkLPConversions(lS(1), 1, vR(1)); 
    checkLPConversions(lP(2), 2, vL(2)); 
    checkLPConversions(lS(2), 2, vL(4)); 
    checkLPConversions(lP(3), 3, vR(3)); 
    checkLPConversions(lS(3), 3, vL(3)); 
    checkLPConversions(lP(4), 4, vR(2)); 
    checkLPConversions(lS(4), 4, vR(4)); 
    checkLPConversions(lP(5), 4, vL(5)); 
    checkLPConversions(lS(5), 4, vR(5)); 
    checkLPConversions(l(1, 0, false), 4, v(1, 0, false)); 
    checkLPConversions(l(1, 0, true), 4, v(1, 0, true)); 
    
    checkVPConversions(vL(0), lP(0), xy(0));
    checkVPConversions(vR(0), lS(0), xy(0));
    checkVPConversions(vL(1), lP(1), xy(10));
    checkVPConversions(vR(1), lS(1), xy(10));
    checkVPConversions(vL(2), lP(2), xy(20));
    checkVPConversions(vR(2), lP(4), xy(20));
    checkVPConversions(vL(3), lS(3), xy(30));
    checkVPConversions(vR(3), lP(3), xy(30));
    checkVPConversions(vL(4), lS(2), xy(40));
    checkVPConversions(vR(4), lS(4), xy(40));
    checkVPConversions(vL(5), lP(5), xy(50));
    checkVPConversions(vR(5), lS(5), xy(50));
    checkVPConversions(v(1, 0, false), l(1, 0, false), xy(0, 10));
    checkVPConversions(v(1, 0, true), l(1, 0, true), xy(0, 10));
    
    checkXYConversion(xy(0),  vR(0));
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

    checkOffsetConversions(0, lP(0), vL(0), vL(6), xy(0), xy(60));
    checkOffsetConversions(1, lP(1), vR(5), vL(5), xy(50));
    checkOffsetConversions(2, lP(2), vR(4), vR(2), xy(40), xy(20));
    checkOffsetConversions(3, lP(3), vL(3), vR(3), xy(30));
    checkOffsetConversions(4, lP(4), vL(4), vL(2), xy(40), xy(20));
    checkOffsetConversions(5, lP(5), vR(1), vL(1), xy(10));
    checkOffsetConversions(6, lP(6), vR(0), vR(6), xy(0), xy(60));
    
    checkLPConversions(lP(0), 0, vL(0));
    checkLPConversions(lS(0), 0, vL(6));
    checkLPConversions(lP(1), 1, vR(5));
    checkLPConversions(lS(1), 1, vL(5));
    checkLPConversions(lP(2), 2, vR(4));
    checkLPConversions(lS(2), 2, vR(2));
    checkLPConversions(lP(3), 3, vL(3));
    checkLPConversions(lS(3), 3, vR(3));
    checkLPConversions(lP(4), 4, vL(4));
    checkLPConversions(lS(4), 4, vL(2));
    checkLPConversions(lP(5), 5, vR(1));
    checkLPConversions(lS(5), 5, vL(1));
    checkLPConversions(lP(6), 6, vR(0));
    checkLPConversions(lS(6), 6, vR(6));

    checkVPConversions(vL(0), lP(0), xy(0));
    checkVPConversions(vR(0), lP(6), xy(0));
    checkVPConversions(vL(1), lS(5), xy(10));
    checkVPConversions(vR(1), lP(5), xy(10));
    checkVPConversions(vL(2), lS(4), xy(20));
    checkVPConversions(vR(2), lS(2), xy(20));
    checkVPConversions(vL(3), lP(3), xy(30));
    checkVPConversions(vR(3), lS(3), xy(30));
    checkVPConversions(vL(4), lP(4), xy(40));
    checkVPConversions(vR(4), lP(2), xy(40));
    checkVPConversions(vL(5), lS(1), xy(50));
    checkVPConversions(vR(5), lP(1), xy(50));
    checkVPConversions(vL(6), lS(0), xy(60));
    checkVPConversions(vR(6), lS(6), xy(60));

    checkXYConversion(xy(1), vR(0));
    checkXYConversion(xy(9), vL(1));
    checkXYConversion(xy(19), vL(2));
    checkXYConversion(xy(21), vR(2));
    checkXYConversion(xy(30), vR(3));
    checkXYConversion(xy(39), vL(4));
    checkXYConversion(xy(41), vR(4));
    checkXYConversion(xy(50), vR(5));
    checkXYConversion(xy(59), vL(6));
    checkXYConversion(xy(61), vR(6));
  }
  
  public void testFolding() throws IOException {
    init("rrrrrr");
    addCollapsedFoldRegion(2, 4, "...");
    
    checkOffsetConversions(0, lP(0), vL(0), vL(2), xy(0), xy(20));
    checkOffsetConversions(1, lP(1), vR(1), vL(1), xy(10));
    checkOffsetConversions(2, lP(2), vR(0), vR(2), xy(0), xy(20));
    checkOffsetConversions(3, lP(3), vR(2), vR(2), xy(20));
    checkOffsetConversions(4, lP(4), vL(5), vL(7), xy(50), xy(70));
    checkOffsetConversions(5, lP(5), vR(6), vL(6), xy(60));
    checkOffsetConversions(6, lP(6), vR(5), vR(7), xy(50), xy(70));
    
    checkLPConversions(lP(0), 0, vL(0));
    checkLPConversions(lS(0), 0, vL(2));
    checkLPConversions(lP(1), 1, vR(1));
    checkLPConversions(lS(1), 1, vL(1));
    checkLPConversions(lP(2), 2, vR(0));
    checkLPConversions(lS(2), 2, vR(2));
    checkLPConversions(lP(3), 3, vR(2));
    checkLPConversions(lS(3), 3, vR(2));
    checkLPConversions(lP(4), 4, vL(5));
    checkLPConversions(lS(4), 4, vL(7));
    checkLPConversions(lP(5), 5, vR(6));
    checkLPConversions(lS(5), 5, vL(6));
    checkLPConversions(lP(6), 6, vR(5));
    checkLPConversions(lS(6), 6, vR(7));
    checkLPConversions(lP(7), 6, vL(8));
    checkLPConversions(lS(7), 6, vR(8));
    
    checkVPConversions(vL(0), lP(0), xy(0));
    checkVPConversions(vR(0), lP(2), xy(0));
    checkVPConversions(vL(1), lS(1), xy(10));
    checkVPConversions(vR(1), lP(1), xy(10));
    checkVPConversions(vL(2), lS(0), xy(20));
    checkVPConversions(vR(2), lS(2), xy(20));
    checkVPConversions(vL(3), lS(2), xy(30));
    checkVPConversions(vR(3), lS(2), xy(30));
    checkVPConversions(vL(4), lS(2), xy(40));
    checkVPConversions(vR(4), lS(2), xy(40));
    checkVPConversions(vL(5), lP(4), xy(50));
    checkVPConversions(vR(5), lP(6), xy(50));
    checkVPConversions(vL(6), lS(5), xy(60));
    checkVPConversions(vR(6), lP(5), xy(60));
    checkVPConversions(vL(7), lS(4), xy(70));
    checkVPConversions(vR(7), lS(6), xy(70));
    checkVPConversions(vL(8), lP(7), xy(80));
    checkVPConversions(vR(8), lS(7), xy(80));
    
    checkXYConversion(xy(1), vR(0));
    checkXYConversion(xy(9), vL(1));
    checkXYConversion(xy(19), vL(2));
    checkXYConversion(xy(21), vR(2));
    checkXYConversion(xy(30), vR(3));
    checkXYConversion(xy(39), vL(4));
    checkXYConversion(xy(41), vR(4));
    checkXYConversion(xy(50), vR(5));
    checkXYConversion(xy(59), vL(6));
    checkXYConversion(xy(69), vL(7));
    checkXYConversion(xy(71), vR(7));
    checkXYConversion(xy(81), vR(8));
  }
  
  public void testFoldingWhenLineContainsSeveralFragments() throws IOException {
    init("r r");
    addCollapsedFoldRegion(1, 2, "...");
    
    checkOffsetConversions(0, lP(0), vL(0), vL(1), xy(0), xy(10));
    checkOffsetConversions(1, lP(1), vR(0), vR(1), xy(0), xy(10));
    checkOffsetConversions(2, lP(2), vL(4), vL(5), xy(40), xy(50));
    checkOffsetConversions(3, lP(3), vR(4), vR(5), xy(40), xy(50));
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
                               visualPositionTowardsSmallerOffsets, ((EditorImpl)myEditor).offsetToVisualPosition(offset, false));
    assertEquals("Wrong beforeOffset->visualLine calculation", 
                 visualPositionTowardsSmallerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertVisualPositionsEqual("Wrong afterOffset->visualPosition calculation",
                               visualPositionTowardsLargerOffsets, ((EditorImpl)myEditor).offsetToVisualPosition(offset, true));
    assertEquals("Wrong afterOffset->visualLine calculation", 
                 visualPositionTowardsLargerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertEquals("Wrong beforeOffset->xy calculation", xyTowardsSmallerOffsets, ((EditorImpl)myEditor).offsetToXY(offset, false));
    assertEquals("Wrong afterOffset->xy calculation", xyTowardsLargerOffsets, ((EditorImpl)myEditor).offsetToXY(offset, true));
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
  
  // logical position leaning to the preceding character
  private static LogicalPosition lP(int column) {
    return new LogicalPosition(0, column);
  }
  
  // logical position leaning to the succeeding character
  private static LogicalPosition lS(int column) {
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
