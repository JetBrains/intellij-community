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
    
    checkOffsetConversions(0, lp(0), vp(0), xy(0));
    checkOffsetConversions(1, lp(1), vp(1), xy(10));
    checkOffsetConversions(2, lp(2), vp(2), vp(4), xy(20), xy(40));
    checkOffsetConversions(3, lp(3), vp(3), xy(30));
    checkOffsetConversions(4, lp(4), vp(2), vp(4), xy(20), xy(40));
    
    checkLPConversions(lp(0), 0, vp(0)); 
    checkLPConversions(lp(1), 1, vp(1)); 
    checkLPConversions(lp(2), 2, vp(2), vp(4)); 
    checkLPConversions(lp(3), 3, vp(3)); 
    checkLPConversions(lp(4), 4, vp(2), vp(4)); 
    checkLPConversions(lp(5), 4, vp(5)); 
    checkLPConversions(lp(1, 0), 4, vp(1, 0)); 
    
    checkVPConversions(vp(0), lp(0), xy(0));
    checkVPConversions(vp(1), lp(1), xy(10));
    checkVPConversions(vp(2), lp(2), lp(4), xy(20));
    checkVPConversions(vp(3), lp(3), xy(30));
    checkVPConversions(vp(4), lp(2), lp(4), xy(40));
    checkVPConversions(vp(5), lp(5), xy(50));
    checkVPConversions(vp(1, 0), lp(1, 0), xy(0, 10));
    
    checkXYConversion(xy(0),  vp(0));
    checkXYConversion(xy(12), vp(1));
    checkXYConversion(xy(19), vp(2));
    checkXYConversion(xy(21), vp(2));
    checkXYConversion(xy(27), vp(3));
    checkXYConversion(xy(39), vp(4));
    checkXYConversion(xy(41), vp(4));
    checkXYConversion(xy(51), vp(5));
  }
  
  public void testNumberInsideRtlText() throws IOException {
    init("rr12rr");

    checkOffsetConversions(0, lp(0), vp(0), vp(6), xy(0), xy(60));
    checkOffsetConversions(1, lp(1), vp(5), xy(50));
    checkOffsetConversions(2, lp(2), vp(4), vp(2), xy(40), xy(20));
    checkOffsetConversions(3, lp(3), vp(3), xy(30));
    checkOffsetConversions(4, lp(4), vp(4), vp(2), xy(40), xy(20));
    checkOffsetConversions(5, lp(5), vp(1), xy(10));
    checkOffsetConversions(6, lp(6), vp(0), vp(6), xy(0), xy(60));
    
    checkLPConversions(lp(0), 0, vp(0), vp(6));
    checkLPConversions(lp(1), 1, vp(5));
    checkLPConversions(lp(2), 2, vp(4), vp(2));
    checkLPConversions(lp(3), 3, vp(3));
    checkLPConversions(lp(4), 4, vp(4), vp(2));
    checkLPConversions(lp(5), 5, vp(1));
    checkLPConversions(lp(6), 6, vp(0), vp(6));

    checkVPConversions(vp(0), lp(0), lp(6), xy(0));
    checkVPConversions(vp(1), lp(5), xy(10));
    checkVPConversions(vp(2), lp(4), lp(2), xy(20));
    checkVPConversions(vp(3), lp(3), xy(30));
    checkVPConversions(vp(4), lp(4), lp(2), xy(40));
    checkVPConversions(vp(5), lp(1), xy(50));
    checkVPConversions(vp(6), lp(0), lp(6), xy(60));

    checkXYConversion(xy(1), vp(0));
    checkXYConversion(xy(9), vp(1));
    checkXYConversion(xy(19), vp(2));
    checkXYConversion(xy(21), vp(2));
    checkXYConversion(xy(30), vp(3));
    checkXYConversion(xy(39), vp(4));
    checkXYConversion(xy(41), vp(4));
    checkXYConversion(xy(50), vp(5));
    checkXYConversion(xy(59), vp(6));
    checkXYConversion(xy(61), vp(6));
  }
  
  public void testFolding() throws IOException {
    init("rrrrrr");
    addCollapsedFoldRegion(2, 4, "...");
    
    checkOffsetConversions(0, lp(0), vp(0), vp(2), xy(0), xy(20));
    checkOffsetConversions(1, lp(1), vp(1), xy(10));
    checkOffsetConversions(2, lp(2), vp(0), vp(2), xy(0), xy(20));
    checkOffsetConversions(3, lp(3), vp(2), xy(20));
    checkOffsetConversions(4, lp(4), vp(5), vp(7), xy(50), xy(70));
    checkOffsetConversions(5, lp(5), vp(6), xy(60));
    checkOffsetConversions(6, lp(6), vp(5), vp(7), xy(50), xy(70));
    
    checkLPConversions(lp(0), 0, vp(0), vp(2));
    checkLPConversions(lp(1), 1, vp(1));
    checkLPConversions(lp(2), 2, vp(0), vp(2));
    checkLPConversions(lp(3), 3, vp(2));
    checkLPConversions(lp(4), 4, vp(5), vp(7));
    checkLPConversions(lp(5), 5, vp(6));
    checkLPConversions(lp(6), 6, vp(5), vp(7));
    checkLPConversions(lp(7), 6, vp(8));
    
    checkVPConversions(vp(0), lp(0), lp(2), xy(0));
    checkVPConversions(vp(1), lp(1), xy(10));
    checkVPConversions(vp(2), lp(0), lp(2), xy(20));
    checkVPConversions(vp(3), lp(2), xy(30));
    checkVPConversions(vp(4), lp(2), xy(40));
    checkVPConversions(vp(5), lp(4), lp(6), xy(50));
    checkVPConversions(vp(6), lp(5), xy(60));
    checkVPConversions(vp(7), lp(4), lp(6), xy(70));
    checkVPConversions(vp(8), lp(7), xy(80));
    
    checkXYConversion(xy(1), vp(0));
    checkXYConversion(xy(9), vp(1));
    checkXYConversion(xy(19), vp(2));
    checkXYConversion(xy(21), vp(2));
    checkXYConversion(xy(30), vp(3));
    checkXYConversion(xy(39), vp(4));
    checkXYConversion(xy(41), vp(4));
    checkXYConversion(xy(50), vp(5));
    checkXYConversion(xy(59), vp(6));
    checkXYConversion(xy(69), vp(7));
    checkXYConversion(xy(71), vp(7));
    checkXYConversion(xy(81), vp(8));
  }
  
  public void testFoldingWhenLineContainsSeveralFragments() throws IOException {
    init("r r");
    addCollapsedFoldRegion(1, 2, "...");
    
    checkOffsetConversions(0, lp(0), vp(0), vp(1), xy(0), xy(10));
    checkOffsetConversions(1, lp(1), vp(0), vp(1), xy(0), xy(10));
    checkOffsetConversions(2, lp(2), vp(4), vp(5), xy(40), xy(50));
    checkOffsetConversions(3, lp(3), vp(4), vp(5), xy(40), xy(50));
  }

  private void init(String text) throws IOException {
    initText(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));
  }

  private static void checkOffsetConversions(int offset,
                                            LogicalPosition logicalPosition, VisualPosition visualPosition, Point xy) {
    checkOffsetConversions(offset, logicalPosition, visualPosition, visualPosition, xy, xy);
  }

  private static void checkOffsetConversions(int offset, 
                                             LogicalPosition logicalPosition, 
                                             VisualPosition visualPositionTowardsSmallerOffsets, 
                                             VisualPosition visualPositionTowardsLargerOffsets, 
                                             Point xyTowardsSmallerOffsets, 
                                             Point xyTowardsLargerOffsets) {
    assertEquals("Wrong offset->logicalPosition calculation", logicalPosition, myEditor.offsetToLogicalPosition(offset));
    assertEquals("Wrong beforeOffset->visualPosition calculation",
                 visualPositionTowardsSmallerOffsets, ((EditorImpl)myEditor).offsetToVisualPosition(offset, false));
    assertEquals("Wrong beforeOffset->visualLine calculation", 
                 visualPositionTowardsSmallerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertEquals("Wrong afterOffset->visualPosition calculation", 
                 visualPositionTowardsLargerOffsets, ((EditorImpl)myEditor).offsetToVisualPosition(offset, true));
    assertEquals("Wrong afterOffset->visualLine calculation", 
                 visualPositionTowardsLargerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertEquals("Wrong beforeOffset->xy calculation", xyTowardsSmallerOffsets, ((EditorImpl)myEditor).offsetToXY(offset, false));
    assertEquals("Wrong afterOffset->xy calculation", xyTowardsLargerOffsets, ((EditorImpl)myEditor).offsetToXY(offset, true));
  }

  private static void checkLPConversions(LogicalPosition logicalPosition, int offset, VisualPosition visualPosition) {
    checkLPConversions(logicalPosition, offset, visualPosition, visualPosition);
  }
  
  private static void checkLPConversions(LogicalPosition logicalPosition,
                                        int offset, 
                                         VisualPosition visualPositionTowardsSmallerLogicalColumns,
                                         VisualPosition visualPositionTowardsLargerLogicalColumns) {
    assertEquals("Wrong logicalPosition->offset calculation", offset, myEditor.logicalPositionToOffset(logicalPosition));
    assertEquals("Wrong beforeLogicalPosition->visualPosition calculation", 
                 visualPositionTowardsSmallerLogicalColumns, ((EditorImpl)myEditor).logicalToVisualPosition(logicalPosition, true, false));
    assertEquals("Wrong afterLogicalPosition->visualPosition calculation",
                 visualPositionTowardsLargerLogicalColumns, ((EditorImpl)myEditor).logicalToVisualPosition(logicalPosition, true, true));
  }

  private static void checkVPConversions(VisualPosition visualPosition, LogicalPosition logicalPosition, Point xy) {
    checkVPConversions(visualPosition, logicalPosition, logicalPosition, xy);
  }

  private static void checkVPConversions(VisualPosition visualPosition, 
                                         LogicalPosition logicalPositionTowardsSmallerVisualColumns, 
                                         LogicalPosition logicalPositionTowardsLargerVisualColumns, 
                                         Point xy) {
    assertEquals("Wrong beforeVisualPosition->logicalPosition calculation", 
                 logicalPositionTowardsSmallerVisualColumns, ((EditorImpl)myEditor).visualToLogicalPosition(visualPosition, true, false));
    assertEquals("Wrong afterVisualPosition->logicalPosition calculation", 
                 logicalPositionTowardsLargerVisualColumns, ((EditorImpl)myEditor).visualToLogicalPosition(visualPosition, true, true));
    assertEquals("Wrong visualPosition->xy calculation", xy, myEditor.visualPositionToXY(visualPosition));
  }
  
  private static void checkXYConversion(Point xy,
                                       VisualPosition visualPosition) {
    assertEquals("Wrong xy->visualPosition calculation", visualPosition, myEditor.xyToVisualPosition(xy));
  }
  
  private static LogicalPosition lp(int column) {
    return new LogicalPosition(0, column);
  }
  
  private static LogicalPosition lp(int line, int column) {
    return new LogicalPosition(line, column);
  }
  
  private static VisualPosition vp(int column) {
    return new VisualPosition(0, column);
  }
  
  private static VisualPosition vp(int line, int column) {
    return new VisualPosition(line, column);
  }
  
  private static Point xy(int x) {
    return new Point(x, 0);
  }
  
  private static Point xy(int x, int y) {
    return new Point(x, y);
  }
}
