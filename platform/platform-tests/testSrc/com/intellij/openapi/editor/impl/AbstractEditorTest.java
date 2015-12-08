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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.softwrap.mapping.CachingSoftWrapDataMapper;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.TestFileType;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.MatchResult;
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

  /**
   * Setups document of the {@link #getEditor() current editor} according to the given text that is expected to contain
   * information about document lines obtained from the {@link DocumentImpl#dumpState()}. 
   * 
   * @param data  string representation of the target document's lines
   */
  @SuppressWarnings("UnusedDeclaration")
  protected static void setupDocument(@NotNull String data) {
    Scanner scanner = new Scanner(data);
    Pattern pattern = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)\\s*-\\s*(\\d+)");
    StringBuilder buffer = new StringBuilder();
    while (scanner.findInLine(pattern) != null) {
      final MatchResult match = scanner.match();
      int startOffset = Integer.parseInt(match.group(2));
      int endOffset = Integer.parseInt(match.group(3));
      buffer.append(StringUtil.repeatSymbol('a', endOffset - startOffset)).append('\n');
    }
    if (buffer.length() > 0) {
      buffer.setLength(buffer.length() - 1);
    }
    myEditor.getDocument().setText(buffer.toString());
  }

  /**
   * Setups {@link Editor#getFoldingModel() folding model} of the {@link #getEditor() current editor} according to the given text
   * that is expected to contain information obtained from the {@link FoldingModelImpl#toString()}.
   * 
   * @param data  string representation of the target fold regions
   */
  protected static void setupFolding(@NotNull String data) {
    Scanner scanner = new Scanner(data);
    Pattern pattern = Pattern.compile("FoldRegion ([+-])\\((\\d+):(\\d+)");
    final List<Trinity<Boolean, Integer, Integer>> infos = new ArrayList<>();
    while (scanner.findInLine(pattern) != null) {
      final MatchResult match = scanner.match();
      boolean expanded = "-".equals(match.group(1));
      int startOffset = Integer.parseInt(match.group(2));
      int endOffset = Integer.parseInt(match.group(3));
      infos.add(new Trinity<>(expanded, startOffset, endOffset));
    }
    final FoldingModel foldingModel = myEditor.getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> {
      for (Trinity<Boolean, Integer, Integer> info : infos) {
        final FoldRegion region = foldingModel.addFoldRegion(info.second, info.third, "...");
        assert region != null;
        region.setExpanded(info.first);
      }
    });
  }

  /**
   * Setups {@link Editor#getSoftWrapModel() soft wraps model} of the {@link #getEditor() current editor} according to the given text
   * that is expected to contain information obtained from the {@link CachingSoftWrapDataMapper#toString()}.
   *
   * @param data  string representation of the target soft wraps cache
   */
  @SuppressWarnings("UnusedDeclaration")
  protected static void setupSoftWraps(@NotNull String data) {
    Scanner scanner = new Scanner(data);
    Pattern generalPattern =
      Pattern.compile("visual line: (\\d+), offsets: (\\d+)-(\\d+), logical lines: (\\d+)-(\\d+), logical columns: (\\d+)-(\\d+), "
                      + "end visual column: (\\d+), fold regions: \\[([^\\]]*)\\], tab data: \\[([^\\]]*)\\]");
    Pattern foldPattern = Pattern.compile("width in columns: (-?\\d+), fold region: FoldRegion [-+]\\((\\d+):(\\d+)");
    Pattern tabPattern = Pattern.compile("\\[(\\d+), width: (\\d+)");
    final SoftWrapModelImpl softWrapModel = (SoftWrapModelImpl)myEditor.getSoftWrapModel();
    final CachingSoftWrapDataMapper mapper = softWrapModel.getDataMapper();
    mapper.release();
    final FoldingModelEx foldingModel = (FoldingModelEx)myEditor.getFoldingModel();
    while (scanner.findInLine(generalPattern) != null) {
      final MatchResult generalMatch = scanner.match();
      int visualLine = Integer.parseInt(generalMatch.group(1));
      int startOffset = Integer.parseInt(generalMatch.group(2));
      int endOffset = Integer.parseInt(generalMatch.group(3));
      int startLogicalLine = Integer.parseInt(generalMatch.group(4));
      int endLogicalLine = Integer.parseInt(generalMatch.group(5));
      int startLogicalColumn = Integer.parseInt(generalMatch.group(6));
      int endLogicalColumn = Integer.parseInt(generalMatch.group(7));
      int endVisualColumn = Integer.parseInt(generalMatch.group(8));
      
      List<Pair<Integer, FoldRegion>> foldRegions = new ArrayList<>();
      Scanner foldScanner = new Scanner(generalMatch.group(9));
      while (foldScanner.findInLine(foldPattern) != null) {
        final MatchResult foldMatch = foldScanner.match();
        int widthInColumns = Integer.parseInt(foldMatch.group(1));
        int foldStartOffset = Integer.parseInt(foldMatch.group(2));
        int foldEndOffset = Integer.parseInt(foldMatch.group(3));
        FoldRegion region = null;
        for (FoldRegion candidate : foldingModel.getAllFoldRegions()) {
          if (candidate.getStartOffset() == foldStartOffset && candidate.getEndOffset() == foldEndOffset) {
            region = candidate;
            break;
          }
        }
        foldRegions.add(new Pair<>(widthInColumns, region));
      }
      
      List<Pair<Integer, Integer>> tabData = new ArrayList<>();
      Scanner tabScanner = new Scanner(generalMatch.group(10));
      while (tabScanner.findInLine(tabPattern) != null) {
        final MatchResult tabMatch = tabScanner.match();
        int offset = Integer.parseInt(tabMatch.group(1));
        int widthInColumns = Integer.parseInt(tabMatch.group(2));
        tabData.add(new Pair<>(offset, widthInColumns));
      }
      
      mapper.rawAdd(visualLine, startOffset, endOffset, startLogicalLine, startLogicalColumn, endLogicalLine, endLogicalColumn, endVisualColumn, foldRegions, tabData);
    }
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
}
