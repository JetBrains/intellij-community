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

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.testFramework.TestFileType;

import java.awt.*;
import java.util.ArrayList;

/**
 * To simplify the representation of input text, 'R' character in these tests represents an RTL character.
 */
public abstract class AbstractRtlTest extends AbstractEditorTest {
  private static final char RTL_CHAR_REPRESENTATION = 'R';
  private static final char RTL_CHAR = '\u05d0'; // Hebrew 'aleph' letter
  private static final char BIDI_BOUNDARY_MARKER = '|';

  protected void prepareText(String text) {
    prepare(text, TestFileType.TEXT);
  }
  
  protected void prepare(String text, TestFileType fileType) {
    init(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR), fileType);
  }
  
  protected void checkResult(String text) {
    checkResultByText(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));
  }
  
  protected static void checkOffsetConversions(int offset,
                                             LogicalPosition logicalPosition,
                                             VisualPosition visualPositionTowardsSmallerOffsets,
                                             VisualPosition visualPositionTowardsLargerOffsets,
                                             Point xy) {
    checkOffsetConversions(offset, logicalPosition, visualPositionTowardsSmallerOffsets, visualPositionTowardsLargerOffsets, xy, xy);
  }

  protected static void checkOffsetConversions(int offset,
                                             LogicalPosition logicalPosition, 
                                             VisualPosition visualPositionTowardsSmallerOffsets, 
                                             VisualPosition visualPositionTowardsLargerOffsets, 
                                             Point xyTowardsSmallerOffsets, 
                                             Point xyTowardsLargerOffsets) {
    assertLogicalPositionsEqual("Wrong offset->logicalPosition calculation", logicalPosition, myEditor.offsetToLogicalPosition(offset));
    assertVisualPositionsEqual("Wrong beforeOffset->visualPosition calculation",
                               visualPositionTowardsSmallerOffsets, myEditor.offsetToVisualPosition(offset, false, false));
    assertVisualPositionsEqual("Wrong afterOffset->visualPosition calculation",
                               visualPositionTowardsLargerOffsets, myEditor.offsetToVisualPosition(offset, true, false));
    assertEquals("Wrong afterOffset->visualLine calculation", 
                 visualPositionTowardsLargerOffsets.line, ((EditorImpl)myEditor).offsetToVisualLine(offset));
    assertEquals("Wrong beforeOffset->xy calculation", xyTowardsSmallerOffsets, myEditor.offsetToXY(offset, false, false));
    assertEquals("Wrong afterOffset->xy calculation", xyTowardsLargerOffsets, myEditor.offsetToXY(offset, true, false));
  }

  protected static void checkLPConversions(int logicalColumn, int offset,
                                         VisualPosition visualPositionForPrecedingLp, VisualPosition visualPositionForSucceedingLp) {
    checkLPConversions(lB(logicalColumn), offset, visualPositionForPrecedingLp);
    checkLPConversions(lF(logicalColumn), offset, visualPositionForSucceedingLp);
    
  }
  
  protected static void checkLPConversions(LogicalPosition logicalPosition, int offset, VisualPosition visualPosition) {
    assertEquals("Wrong logicalPosition->offset calculation", offset, myEditor.logicalPositionToOffset(logicalPosition));
    assertVisualPositionsEqual("Wrong logicalPosition->visualPosition calculation",
                               visualPosition, myEditor.logicalToVisualPosition(logicalPosition));
  }

  protected static void checkVPConversions(int visualColumn, LogicalPosition logicalPositionForLeftLeaningVp,
                                         LogicalPosition logicalPositionForRightLeaningVp, Point xy) {
    checkVPConversions(vL(visualColumn), logicalPositionForLeftLeaningVp, xy);
    checkVPConversions(vR(visualColumn), logicalPositionForRightLeaningVp, xy);
  }

  protected static void checkVPConversions(VisualPosition visualPosition, LogicalPosition logicalPosition, Point xy) {
    assertLogicalPositionsEqual("Wrong visualPosition->logicalPosition calculation",
                                logicalPosition, myEditor.visualToLogicalPosition(visualPosition));
    assertEquals("Wrong visualPosition->xy calculation", xy, myEditor.visualPositionToXY(visualPosition));
  }
  
  protected static void checkXYConversion(Point xy,
                                       VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong xy->visualPosition calculation", visualPosition, myEditor.xyToVisualPosition(xy));
  }

  protected static void assertLogicalPositionsEqual(String message, LogicalPosition expectedPosition, LogicalPosition actualPosition) {
    assertEquals(message, expectedPosition, actualPosition);
    assertEquals(message + " (direction flag)", expectedPosition.leansForward, actualPosition.leansForward);
  }

  protected static void assertVisualPositionsEqual(String message, VisualPosition expectedPosition, VisualPosition actualPosition) {
    assertEquals(message, expectedPosition, actualPosition);
    assertEquals(message + " (direction flag)", expectedPosition.leansRight, actualPosition.leansRight);
  }

  protected static void assertCaretPosition(VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong caret position", visualPosition, myEditor.getCaretModel().getVisualPosition());
  }

  protected static void assertVisualCaretLocation(int visualColumn, boolean reversedDirection) {
    assertVisualCaretLocation(0, visualColumn, reversedDirection);
  }
  
  protected static void assertVisualCaretLocation(int visualLine, int visualColumn, boolean reversedDirection) {
    assertEquals(1, myEditor.getCaretModel().getCaretCount());
    Caret caret = myEditor.getCaretModel().getPrimaryCaret();
    assertEquals(visualLine, caret.getVisualPosition().line);
    assertEquals(visualColumn, caret.getVisualPosition().column);
    assertEquals(reversedDirection, caret.isAtRtlLocation());
  }

  /**
   * Text should contain {@link #BIDI_BOUNDARY_MARKER} characters at expected bidi run boundaries' positions.
   */
  protected void checkBidiRunBoundaries(String textWithBoundaryMarkers, String fileExtension) {
    java.util.List<Integer> expectedBoundaryPositions = new ArrayList<>();
    StringBuilder rawTextBuilder = new StringBuilder();
    for (int i = 0; i < textWithBoundaryMarkers.length(); i++) {
      char c = textWithBoundaryMarkers.charAt(i);
      if (c == BIDI_BOUNDARY_MARKER) {
        expectedBoundaryPositions.add(rawTextBuilder.length());
      }
      else {
        rawTextBuilder.append(c);
      }
    }
    String rawText = rawTextBuilder.toString();
    configureFromFileText(getTestName(false) + "." + fileExtension, rawText.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));

    java.util.List<Integer> actualBoundaryPositions = new ArrayList<>();
    for (int i = 1; i < rawText.length(); i++) {
      if (!myEditor.offsetToVisualPosition(i, false, false).equals(
        myEditor.offsetToVisualPosition(i, true, false))) {
        actualBoundaryPositions.add(i);
      }
    }
    assertEquals("Unexpected bidi regions boundaries' positions", expectedBoundaryPositions, actualBoundaryPositions);
  }

  // logical position leaning backward
  protected static LogicalPosition lB(int column) {
    return new LogicalPosition(0, column);
  }
  
  // logical position leaning backward
  protected static LogicalPosition lB(int line, int column) {
    return new LogicalPosition(line, column);
  }

  // logical position leaning forward
  protected static LogicalPosition lF(int column) {
    return new LogicalPosition(0, column, true);
  }
  
  // logical position leaning forward
  protected static LogicalPosition lF(int line, int column) {
    return new LogicalPosition(line, column, true);
  }
 
  // visual position leaning to the left
  protected static VisualPosition vL(int column) {
    return new VisualPosition(0, column);
  }

  // visual position leaning to the left
  protected static VisualPosition vL(int line, int column) {
    return new VisualPosition(line, column);
  }

  // visual position leaning to the right
  protected static VisualPosition vR(int column) {
    return new VisualPosition(0, column, true);
  }

  // visual position leaning to the right
  protected static VisualPosition vR(int line, int column) {
    return new VisualPosition(line, column, true);
  }

  protected static Point xy(int x) {
    return new Point(x, 0);
  }
  
  protected static Point xy(int x, int y) {
    return new Point(x, y);
  }
}
