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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.TestFileType;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;

/**
 * Base super class for tests that check various IJ editor functionality on managed document modification.
 * <p/>
 * It's main purpose is to provide utility methods like fold regions addition and setup; typing etc. 
 * 
 * @author Denis Zhdanov
 * @since 11/18/10 7:43 PM
 */
public abstract class AbstractEditorTest extends LightPlatformCodeInsightTestCase {
  public static final int TEST_CHAR_WIDTH = 10; // char width matches the one in EditorTestUtil.configureSoftWraps
  public static final int TEST_LINE_HEIGHT = 10;
  public static final int TEST_DESCENT = 2;
  
  public static final String LOREM_IPSUM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.";

  public static final String SURROGATE_PAIR = new String(new int[]{Character.MIN_SUPPLEMENTARY_CODE_POINT}, 0, 1);
  public static final String HIGH_SURROGATE = SURROGATE_PAIR.substring(0, 1);
  public static final String LOW_SURROGATE = SURROGATE_PAIR.substring(1, 2);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FontLayoutService.setInstance(null);
    }
    finally {
      super.tearDown();
    }
  }

  protected void initText(@NotNull @NonNls String fileText) throws IOException {
    init(fileText, TestFileType.TEXT);
  }
  
  protected void init(@NotNull @NonNls String fileText, @NotNull TestFileType type) throws IOException {
    configureFromFileText(getFileName(type), fileText);
  }

  private String getFileName(TestFileType type) {
    return getTestName(false) + type.getExtension();
  }

  protected static FoldRegion addFoldRegion(final int startOffset, final int endOffset, final String placeholder) {
    final FoldRegion[] result = new FoldRegion[1];
    myEditor.getFoldingModel().runBatchFoldingOperation(
      () -> result[0] = myEditor.getFoldingModel().addFoldRegion(startOffset, endOffset, placeholder));
    return result[0];
  }

  protected static FoldRegion addCollapsedFoldRegion(final int startOffset, final int endOffset, final String placeholder) {
    FoldRegion region = addFoldRegion(startOffset, endOffset, placeholder);
    toggleFoldRegionState(region, false);
    return region;
  }

  protected static void toggleFoldRegionState(final FoldRegion foldRegion, final boolean expanded) {
    myEditor.getFoldingModel().runBatchFoldingOperation(() -> foldRegion.setExpanded(expanded));
  }

  protected static FoldRegion getFoldRegion(int startOffset) {
    FoldRegion[] foldRegions = myEditor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion foldRegion : foldRegions) {
      if (foldRegion.getStartOffset() == startOffset) {
        return foldRegion;
      }
    }
    throw new IllegalArgumentException(String.format(
      "Can't find fold region with start offset %d. Registered fold regions: %s. Document text: '%s'",
      startOffset, Arrays.toString(foldRegions), myEditor.getDocument().getCharsSequence()
    ));
  }

  protected static void foldOccurrences(String textToFoldRegexp, final String placeholder) {
    final Matcher matcher = Pattern.compile(textToFoldRegexp).matcher(myEditor.getDocument().getCharsSequence());
    myEditor.getFoldingModel().runBatchFoldingOperation(() -> {
      while (matcher.find()) {
        FoldRegion foldRegion = myEditor.getFoldingModel().addFoldRegion(matcher.start(), matcher.end(), placeholder);
        assertNotNull(foldRegion);
        foldRegion.setExpanded(false);
      }
    });
  }

  public void assertSelectionRanges(int[][] ranges) {
    int[] selectionStarts = myEditor.getSelectionModel().getBlockSelectionStarts();
    int[] selectionEnds = myEditor.getSelectionModel().getBlockSelectionEnds();
    int actualRangeCount = selectionStarts.length;
    int[][] actualRanges = new int[actualRangeCount][];
    for (int i = 0; i < actualRangeCount; i++) {
      actualRanges[i] = new int[] {selectionStarts[i], selectionEnds[i]};
    }
    assertEquals("Wrong selected ranges", Arrays.deepToString(ranges), Arrays.deepToString(actualRanges));
  }

  public EditorMouseFixture mouse() {
    return new EditorMouseFixture((EditorImpl)myEditor);
  }

  public void setEditorVisibleSize(int widthInChars, int heightInChars) {
    EditorTestUtil.setEditorVisibleSize(myEditor, widthInChars, heightInChars);
  }

  /**
   * Verifies visual positions of carets and their selection ranges. It's assumed that for each caret its position and selection range
   * are within the same visual line.
   *
   * For each caret its visual position and visual positions of selection start an and should be provided in the following order:
   * line, caretColumn, selectionStartColumn, selectionEndColumn
   */
  public static void verifyCaretsAndSelections(int... coordinates) {
    int caretCount = coordinates.length / 4;
    List<Caret> carets = myEditor.getCaretModel().getAllCarets();
    assertEquals("Unexpected caret count", caretCount, carets.size());
    for (int i = 0; i < caretCount; i++) {
      Caret caret = carets.get(i);
      assertEquals("Unexpected position for caret " + (i + 1), new VisualPosition(coordinates[i * 4], coordinates[i * 4 + 1]), caret.getVisualPosition());
      assertEquals("Unexpected selection start for caret " + (i + 1), new VisualPosition(coordinates[i * 4], coordinates[i * 4 + 2]), caret.getSelectionStartPosition());
      assertEquals("Unexpected selection end for caret " + (i + 1), new VisualPosition(coordinates[i * 4], coordinates[i * 4 + 3]), caret.getSelectionEndPosition());
    }
  }

  public static void verifySoftWrapPositions(Integer... positions) {
    List<Integer> softWrapPositions = new ArrayList<>();
    for (SoftWrap softWrap : myEditor.getSoftWrapModel().getSoftWrapsForRange(0, myEditor.getDocument().getTextLength())) {
      softWrapPositions.add(softWrap.getStart());
    }
    assertArrayEquals(positions, softWrapPositions.toArray());
  }

  protected static void configureSoftWraps(int charCountToWrapAt) {
    EditorTestUtil.configureSoftWraps(myEditor, charCountToWrapAt);
  }

  public static Inlay addInlay(int offset) {
    return addInlay(offset, false);
  }

  public static Inlay addInlay(int offset, boolean relatesToPrecedingText) {
    return EditorTestUtil.addInlay(myEditor, offset, relatesToPrecedingText);
  }

  protected static void runWriteCommand(ThrowableRunnable r) {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        r.run();
      }
    }.execute();
  }

  protected static void runFoldingOperation(Runnable r) {
    myEditor.getFoldingModel().runBatchFoldingOperation(r);
  }
}
