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
    
    checkOffsetToEverythingCalculation(0,         lp(0, 0), vp(0, 0), xy(0, 0));
    checkOffsetToEverythingCalculation(1,         lp(0, 1), vp(0, 1), xy(10, 0));
    checkOffsetToEverythingCalculation(2, false,  lp(0, 2), vp(0, 2), xy(20, 0));
    checkOffsetToEverythingCalculation(2, true,   lp(0, 2), vp(0, 2), xy(40, 0));
    checkOffsetToEverythingCalculation(3,         lp(0, 3), vp(0, 3), xy(30, 0));
    checkOffsetToEverythingCalculation(4, false,  lp(0, 4), vp(0, 4), xy(20, 0));
    checkOffsetToEverythingCalculation(4, true,   lp(0, 4), vp(0, 4), xy(40, 0));    
    
    checkLogicalPositionToEverythingCalculation(lp(0, 0), 0, vp(0, 0)); 
    checkLogicalPositionToEverythingCalculation(lp(0, 1), 1, vp(0, 1)); 
    checkLogicalPositionToEverythingCalculation(lp(0, 2), 2, vp(0, 2)); 
    checkLogicalPositionToEverythingCalculation(lp(0, 3), 3, vp(0, 3)); 
    checkLogicalPositionToEverythingCalculation(lp(0, 4), 4, vp(0, 4)); 
    checkLogicalPositionToEverythingCalculation(lp(0, 5), 4, vp(0, 5)); 
    checkLogicalPositionToEverythingCalculation(lp(1, 0), 4, vp(1, 0)); 
    
    checkVisualPositionToEverythingCalculation(vp(0, 0),        lp(0, 0), xy(0, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 1),        lp(0, 1), xy(10, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 2), false, lp(0, 2), xy(20, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 2), true,  lp(0, 2), xy(40, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 3),        lp(0, 3), xy(30, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 4), false, lp(0, 4), xy(20, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 4), true,  lp(0, 4), xy(40, 0));
    checkVisualPositionToEverythingCalculation(vp(0, 5),        lp(0, 5), xy(50, 0));
    checkVisualPositionToEverythingCalculation(vp(1, 0),        lp(1, 0), xy(0, 10));
    
    checkXYToVisualPositionCalculation(xy(0,  0), vp(0, 0));
    checkXYToVisualPositionCalculation(xy(12, 0), vp(0, 1));
    checkXYToVisualPositionCalculation(xy(19, 0), vp(0, 2));
    checkXYToVisualPositionCalculation(xy(21, 0), vp(0, 4));
    checkXYToVisualPositionCalculation(xy(27, 0), vp(0, 3));
    checkXYToVisualPositionCalculation(xy(39, 0), vp(0, 2));
    checkXYToVisualPositionCalculation(xy(41, 0), vp(0, 4));
    checkXYToVisualPositionCalculation(xy(51, 0), vp(0, 5));
  }

  private void init(String text) throws IOException {
    initText(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));
  }

  private static void checkOffsetToEverythingCalculation(int offset,
                                                         LogicalPosition logicalPosition, VisualPosition visualPosition, Point xy) {
    checkOffsetToEverythingCalculation(offset, false, logicalPosition, visualPosition, xy);
    checkOffsetToEverythingCalculation(offset, true, logicalPosition, visualPosition, xy);
  }

  private static void checkOffsetToEverythingCalculation(int offset, boolean leanToLargerOffsets,
                                                         LogicalPosition logicalPosition, VisualPosition visualPosition, Point xy) {
    assertEquals("Wrong offset->logicalPosition calculation", logicalPosition, myEditor.offsetToLogicalPosition(offset));
    assertEquals("Wrong offset->visualPosition calculation", visualPosition, myEditor.offsetToVisualPosition(offset));
    assertEquals("Wrong offset->visualLine calculation", visualPosition.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertEquals("Wrong offset->xy calculation", xy, ((EditorImpl)myEditor).offsetToXY(offset, leanToLargerOffsets));
  }

  private static void checkLogicalPositionToEverythingCalculation(LogicalPosition logicalPosition, 
                                                                  int offset, VisualPosition visualPosition) {
    assertEquals("Wrong logicalPosition->offset calculation", offset, myEditor.logicalPositionToOffset(logicalPosition));
    assertEquals("Wrong logicalPosition->visualPosition calculation", visualPosition, myEditor.logicalToVisualPosition(logicalPosition));
  }

  private static void checkVisualPositionToEverythingCalculation(VisualPosition visualPosition,
                                                                 LogicalPosition logicalPosition, Point xy) {
    checkVisualPositionToEverythingCalculation(visualPosition, false, logicalPosition, xy);
    checkVisualPositionToEverythingCalculation(visualPosition, true, logicalPosition, xy);
  }

  private static void checkVisualPositionToEverythingCalculation(VisualPosition visualPosition, boolean leanTowardsLargerColumns,
                                                                 LogicalPosition logicalPosition, Point xy) {
    assertEquals("Wrong visualPosition->logicalPosition calculation", logicalPosition, myEditor.visualToLogicalPosition(visualPosition));
    assertEquals("Wrong visualPosition->xy calculation", xy,
                 ((EditorImpl)myEditor).visualPositionToXY(visualPosition, leanTowardsLargerColumns));
  }
  
  private static void checkXYToVisualPositionCalculation(Point xy, 
                                                         VisualPosition visualPosition) {
    assertEquals("Wrong xy->visualPosition calculation", visualPosition, myEditor.xyToVisualPosition(xy));
  }
  
  private static LogicalPosition lp(int line, int column) {
    return new LogicalPosition(line, column);
  }
  
  private static VisualPosition vp(int line, int column) {
    return new VisualPosition(line, column);
  }
  
  private static Point xy(int x, int y) {
    return new Point(x, y);
  }
}
