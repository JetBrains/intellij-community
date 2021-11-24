// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;

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
    prepare(text, PlainTextFileType.INSTANCE);
  }
  
  protected void prepare(String text, FileType fileType) {
    init(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR), fileType);
  }
  
  protected void checkResult(String text) {
    checkResultByText(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));
  }
  
  protected void checkOffsetConversions(int offset,
                                        LogicalPosition logicalPosition,
                                        VisualPosition visualPositionTowardsSmallerOffsets,
                                        VisualPosition visualPositionTowardsLargerOffsets,
                                        Point xy) {
    checkOffsetConversions(offset, logicalPosition, visualPositionTowardsSmallerOffsets, visualPositionTowardsLargerOffsets, xy, xy);
  }

  protected void checkOffsetConversions(int offset,
                                        LogicalPosition logicalPosition,
                                        VisualPosition visualPositionTowardsSmallerOffsets,
                                        VisualPosition visualPositionTowardsLargerOffsets,
                                        Point xyTowardsSmallerOffsets,
                                        Point xyTowardsLargerOffsets) {
    assertLogicalPositionsEqual("Wrong offset->logicalPosition calculation", logicalPosition, getEditor().offsetToLogicalPosition(offset));
    assertVisualPositionsEqual("Wrong beforeOffset->visualPosition calculation",
                               visualPositionTowardsSmallerOffsets, getEditor().offsetToVisualPosition(offset, false, false));
    assertVisualPositionsEqual("Wrong afterOffset->visualPosition calculation",
                               visualPositionTowardsLargerOffsets, getEditor().offsetToVisualPosition(offset, true, false));
    assertEquals("Wrong afterOffset->visualLine calculation", 
                 visualPositionTowardsLargerOffsets.line, ((EditorImpl)getEditor()).offsetToVisualLine(offset));
    assertEquals("Wrong beforeOffset->xy calculation", xyTowardsSmallerOffsets, getEditor().offsetToXY(offset, false, false));
    assertEquals("Wrong afterOffset->xy calculation", xyTowardsLargerOffsets, getEditor().offsetToXY(offset, true, false));
  }

  protected void checkLPConversions(int logicalColumn, int offset,
                                    VisualPosition visualPositionForPrecedingLp, VisualPosition visualPositionForSucceedingLp) {
    checkLPConversions(lB(logicalColumn), offset, visualPositionForPrecedingLp);
    checkLPConversions(lF(logicalColumn), offset, visualPositionForSucceedingLp);
    
  }
  
  protected void checkLPConversions(LogicalPosition logicalPosition, int offset, VisualPosition visualPosition) {
    assertEquals("Wrong logicalPosition->offset calculation", offset, getEditor().logicalPositionToOffset(logicalPosition));
    assertVisualPositionsEqual("Wrong logicalPosition->visualPosition calculation",
                               visualPosition, getEditor().logicalToVisualPosition(logicalPosition));
  }

  protected void checkVPConversions(int visualColumn, LogicalPosition logicalPositionForLeftLeaningVp,
                                    LogicalPosition logicalPositionForRightLeaningVp, Point xy) {
    checkVPConversions(vL(visualColumn), logicalPositionForLeftLeaningVp, xy);
    checkVPConversions(vR(visualColumn), logicalPositionForRightLeaningVp, xy);
  }

  protected void checkVPConversions(VisualPosition visualPosition, LogicalPosition logicalPosition, Point xy) {
    assertLogicalPositionsEqual("Wrong visualPosition->logicalPosition calculation",
                                logicalPosition, getEditor().visualToLogicalPosition(visualPosition));
    assertEquals("Wrong visualPosition->xy calculation", xy, getEditor().visualPositionToXY(visualPosition));
  }
  
  protected void checkXYConversion(Point xy,
                                   VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong xy->visualPosition calculation", visualPosition, getEditor().xyToVisualPosition(xy));
  }

  protected static void assertLogicalPositionsEqual(String message, LogicalPosition expectedPosition, LogicalPosition actualPosition) {
    assertEquals(message, expectedPosition, actualPosition);
    assertEquals(message + " (direction flag)", expectedPosition.leansForward, actualPosition.leansForward);
  }

  protected static void assertVisualPositionsEqual(String message, VisualPosition expectedPosition, VisualPosition actualPosition) {
    assertEquals(message, expectedPosition, actualPosition);
    assertEquals(message + " (direction flag)", expectedPosition.leansRight, actualPosition.leansRight);
  }

  protected void assertCaretPosition(VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong caret position", visualPosition, getEditor().getCaretModel().getVisualPosition());
  }

  protected void assertVisualCaretLocation(int visualColumn, boolean reversedDirection) {
    assertVisualCaretLocation(0, visualColumn, reversedDirection);
  }
  
  protected void assertVisualCaretLocation(int visualLine, int visualColumn, boolean reversedDirection) {
    assertEquals(1, getEditor().getCaretModel().getCaretCount());
    Caret caret = getEditor().getCaretModel().getPrimaryCaret();
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
      if (!getEditor().offsetToVisualPosition(i, false, false).equals(
        getEditor().offsetToVisualPosition(i, true, false))) {
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

  protected static int ls(int h) {
    return (int)(FontPreferences.DEFAULT_LINE_SPACING * h);
  }
}
