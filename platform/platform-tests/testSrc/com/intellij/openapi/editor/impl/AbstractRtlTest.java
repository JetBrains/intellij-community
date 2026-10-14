// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;

import java.awt.Point;
import java.util.ArrayList;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * To simplify the representation of input text, 'R' character in these tests represents an RTL character.
 */
public abstract class AbstractRtlTest {
  private static final char RTL_CHAR_REPRESENTATION = 'R';
  private static final char RTL_CHAR = 'א'; // Hebrew 'aleph' letter
  private static final char BIDI_BOUNDARY_MARKER = '|';

  @SuppressWarnings("deprecation")
  protected static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  protected EditorModelFixture myFixture;

  @BeforeEach
  void setUpAbstractRtlTest() {
    myFixture = editorFixture.get();
  }

  protected void prepareText(String text) {
    prepare(text, PlainTextFileType.INSTANCE);
  }
  
  protected void prepare(String text, FileType fileType) {
    myFixture.init(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR), fileType);
  }
  
  protected void checkResult(String text) {
    myFixture.checkResultByText(text.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));
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
    assertLogicalPositionsEqual("Wrong offset->logicalPosition calculation",
                                logicalPosition, myFixture.getEditor().offsetToLogicalPosition(offset));
    assertVisualPositionsEqual("Wrong beforeOffset->visualPosition calculation",
                               visualPositionTowardsSmallerOffsets, myFixture.getEditor().offsetToVisualPosition(offset, false, false));
    assertVisualPositionsEqual("Wrong afterOffset->visualPosition calculation",
                               visualPositionTowardsLargerOffsets, myFixture.getEditor().offsetToVisualPosition(offset, true, false));
    assertEquals(visualPositionTowardsLargerOffsets.line, 
                 ((EditorImpl)myFixture.getEditor()).offsetToVisualLine(offset), "Wrong afterOffset->visualLine calculation");
    assertEquals(xyTowardsSmallerOffsets, myFixture.getEditor().offsetToXY(offset, false, false), "Wrong beforeOffset->xy calculation");
    assertEquals(xyTowardsLargerOffsets, myFixture.getEditor().offsetToXY(offset, true, false), "Wrong afterOffset->xy calculation");
  }

  protected void checkLPConversions(int logicalColumn, int offset,
                                    VisualPosition visualPositionForPrecedingLp, VisualPosition visualPositionForSucceedingLp) {
    checkLPConversions(lB(logicalColumn), offset, visualPositionForPrecedingLp);
    checkLPConversions(lF(logicalColumn), offset, visualPositionForSucceedingLp);
    
  }
  
  protected void checkLPConversions(LogicalPosition logicalPosition, int offset, VisualPosition visualPosition) {
    assertEquals(offset, myFixture.getEditor().logicalPositionToOffset(logicalPosition), "Wrong logicalPosition->offset calculation");
    assertVisualPositionsEqual("Wrong logicalPosition->visualPosition calculation",
                               visualPosition, myFixture.getEditor().logicalToVisualPosition(logicalPosition));
  }

  protected void checkVPConversions(int visualColumn, LogicalPosition logicalPositionForLeftLeaningVp,
                                    LogicalPosition logicalPositionForRightLeaningVp, Point xy) {
    checkVPConversions(vL(visualColumn), logicalPositionForLeftLeaningVp, xy);
    checkVPConversions(vR(visualColumn), logicalPositionForRightLeaningVp, xy);
  }

  protected void checkVPConversions(VisualPosition visualPosition, LogicalPosition logicalPosition, Point xy) {
    assertLogicalPositionsEqual("Wrong visualPosition->logicalPosition calculation",
                                logicalPosition, myFixture.getEditor().visualToLogicalPosition(visualPosition));
    assertEquals(xy, myFixture.getEditor().visualPositionToXY(visualPosition), "Wrong visualPosition->xy calculation");
  }
  
  protected void checkXYConversion(Point xy,
                                   VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong xy->visualPosition calculation", visualPosition, myFixture.getEditor().xyToVisualPosition(xy));
  }

  protected static void assertLogicalPositionsEqual(String message, LogicalPosition expectedPosition, LogicalPosition actualPosition) {
    assertEquals(expectedPosition, actualPosition, message);
    assertEquals(expectedPosition.leansForward, actualPosition.leansForward, message + " (direction flag)");
  }

  protected static void assertVisualPositionsEqual(String message, VisualPosition expectedPosition, VisualPosition actualPosition) {
    assertEquals(expectedPosition, actualPosition, message);
    assertEquals(expectedPosition.leansRight, actualPosition.leansRight, message + " (direction flag)");
  }

  protected void assertCaretPosition(VisualPosition visualPosition) {
    assertVisualPositionsEqual("Wrong caret position", visualPosition, myFixture.getEditor().getCaretModel().getVisualPosition());
  }

  protected void assertVisualCaretLocation(int visualColumn, boolean reversedDirection) {
    assertVisualCaretLocation(0, visualColumn, reversedDirection);
  }
  
  protected void assertVisualCaretLocation(int visualLine, int visualColumn, boolean reversedDirection) {
    assertEquals(1, myFixture.getEditor().getCaretModel().getCaretCount());
    Caret caret = myFixture.getEditor().getCaretModel().getPrimaryCaret();
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
    myFixture.configureFromFileText(myFixture.getTestName() + "." + fileExtension, rawText.replace(RTL_CHAR_REPRESENTATION, RTL_CHAR));

    java.util.List<Integer> actualBoundaryPositions = new ArrayList<>();
    for (int i = 1; i < rawText.length(); i++) {
      if (!myFixture.getEditor().offsetToVisualPosition(i, false, false).equals(
        myFixture.getEditor().offsetToVisualPosition(i, true, false))) {
        actualBoundaryPositions.add(i);
      }
    }
    assertEquals(expectedBoundaryPositions, actualBoundaryPositions, "Unexpected bidi regions boundaries' positions");
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
