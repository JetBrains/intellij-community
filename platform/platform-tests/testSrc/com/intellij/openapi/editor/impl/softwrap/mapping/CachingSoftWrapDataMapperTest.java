/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.mock.MockFoldRegion;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 07/07/2010
 */
public class CachingSoftWrapDataMapperTest {

  private static final Comparator<DataEntry> LOGICAL_POSITIONS_COMPARATOR = (o1, o2) -> {
    LogicalPosition logical1 = o1.logical;
    LogicalPosition logical2 = o2.logical;
    if (logical1.line != logical2.line) {
      return logical1.line - logical2.line;
    }

    // There is a possible case that multiple logical positions match to the same visual position (e.g. logical
    // positions for folded text match to the same visual position). We want to match to the logical position of
    // folding region start if we search by logical position from folded text.
    if (o1.foldedSpace && o2.foldedSpace && logical1.column + logical1.foldingColumnDiff == logical2.foldingColumnDiff) {
      return o1.foldedSpace ? 1 : -1;
    }
    return logical1.column - logical2.column;
  };

  private static final Comparator<DataEntry> OFFSETS_COMPARATOR = (o1, o2) -> {
    if (o1.offset != o2.offset) {
      return o1.offset - o2.offset;
    }
    // There are numerous situations when multiple visual positions share the same offset (e.g. all soft wrap-introduced virtual
    // spaces share offset with the first document symbol after soft wrap or all virtual spaces after line end share the same offset
    // as the last line symbol). We want to ignore such positions during lookup by offset.
    if (o1.virtualSpace ^ o2.virtualSpace) {
      return o1.virtualSpace ? 1 : -1;
    }
    if (o1.insideTab ^ o2.insideTab) {
      return o1.insideTab ? 1 : -1;
    }
    return 0;
  };

  private static final String SOFT_WRAP_START_MARKER  = "<WRAP>";
  private static final String SOFT_WRAP_END_MARKER    = "</WRAP>";
  private static final String FOLDING_START_MARKER    = "<FOLD>";
  private static final String FOLDING_END_MARKER      = "</FOLD>";
  private static final int    TAB_SIZE                = 4;
  private static final int    SPACE_SIZE              = 7;
  private static final int    SOFT_WRAP_DRAWING_WIDTH = 11;

  /** Holds expected mappings between visual and logical positions and offset. */
  private final List<DataEntry>  myExpectedData  = new ArrayList<>();
  private final List<TextRange>  myLineRanges    = new ArrayList<>();
  /** Holds document offsets that are considered to be folded. */
  private final TIntHashSet      myFoldedOffsets = new TIntHashSet();
  private final List<FoldRegion> myFoldRegions   = new ArrayList<>();

  private CachingSoftWrapDataMapper          myMapper;
  private Mockery                            myMockery;
  private EditorEx                           myEditor;
  private DocumentEx                         myDocument;
  private StringBuilder                      myChars;
  private SoftWrapsStorage                   myStorage;
  private SoftWrapModelEx                    mySoftWrapModel;
  private FoldingModelEx                     myFoldingModel;
  private MockEditorTextRepresentationHelper myRepresentationHelper;
    
  @Before
  public void setUp() {
    myMockery = new JUnit4Mockery();

    myEditor = myMockery.mock(EditorEx.class);
    myDocument = myMockery.mock(DocumentEx.class);
    myChars = new StringBuilder();
    myStorage = new SoftWrapsStorage();
    mySoftWrapModel = myMockery.mock(SoftWrapModelEx.class);
    myFoldingModel = myMockery.mock(FoldingModelEx.class);
    final EditorSettings settings = myMockery.mock(EditorSettings.class);
    final Project project = myMockery.mock(Project.class);
    final SoftWrapPainter painter = myMockery.mock(SoftWrapPainter.class);

    myRepresentationHelper = new MockEditorTextRepresentationHelper(myChars, SPACE_SIZE, TAB_SIZE);

    myMockery.checking(new Expectations() {{
      // Document
      allowing(myEditor).getDocument();will(returnValue(myDocument));
      allowing(myDocument).getLineCount(); will(new CustomAction("getLineCount()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myLineRanges.size();
        }
      });
      allowing(myDocument).getLineNumber(with(any(int.class))); will(new CustomAction("getLineNumber()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getLineNumber((Integer)invocation.getParameter(0));
        }
      });
      allowing(myDocument).getLineStartOffset(with(any(int.class))); will(new CustomAction("getLineStart()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getLineStartOffset((Integer)invocation.getParameter(0));
        }
      });
      allowing(myDocument).getLineEndOffset(with(any(int.class))); will(new CustomAction("getLineEnd()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getLineEndOffset((Integer)invocation.getParameter(0));
        }
      });

      // Settings.
      allowing(myEditor).getSettings();will(returnValue(settings));
      allowing(settings).isUseSoftWraps();will(returnValue(true));
      allowing(settings).getTabSize(project);will(returnValue(TAB_SIZE));
      allowing(settings).isWhitespacesShown();will(returnValue(true));
      allowing(myEditor).getProject();will(returnValue(project));

      // Soft wraps.
      allowing(myEditor).getSoftWrapModel(); will(returnValue(mySoftWrapModel));
      allowing(mySoftWrapModel).getSoftWrap(with(any(int.class))); will(new CustomAction("getSoftWrap") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getSoftWrap((Integer)invocation.getParameter(0));
        }
      });
      allowing(mySoftWrapModel).getEditorTextRepresentationHelper(); will(returnValue(myRepresentationHelper));

      // Folding.
      allowing(myEditor).getFoldingModel();will(returnValue(myFoldingModel));
      allowing(myFoldingModel).isFoldingEnabled(); returnValue(true);
      allowing(myFoldingModel).setFoldingEnabled(with(any(boolean.class)));
      allowing(myFoldingModel).isOffsetCollapsed(with(any(int.class))); will(new CustomAction("isOffsetCollapsed()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myFoldedOffsets.contains((Integer)invocation.getParameter(0));
        }
      });
      allowing(myFoldingModel).getCollapsedRegionAtOffset(with(any(int.class))); will(new CustomAction("getCollapsedRegionAtOffset()") {
        @Nullable
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getCollapsedFoldRegion((Integer)invocation.getParameter(0));
        }
      });
      allowing(myFoldingModel).fetchTopLevel(); will(new CustomAction("fetchTopLevel()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myFoldRegions.toArray(new FoldRegion[myFoldRegions.size()]);
        }
      });

      // Soft wrap-unaware conversions.
      allowing(myEditor).logicalToVisualPosition(with(any(LogicalPosition.class))); will(new CustomAction("logical2visual()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return logicalToVisual((LogicalPosition)invocation.getParameter(0));
        }
      });
      allowing(myEditor).logicalPositionToOffset(with(any(LogicalPosition.class))); will(new CustomAction("logical2offset()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return logicalToOffset((LogicalPosition)invocation.getParameter(0));
        }
      });
      allowing(myEditor).offsetToLogicalPosition(with(any(int.class))); will(new CustomAction("offset2logical()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return offsetToLogical((Integer)invocation.getParameter(0));
        }
      });

      // Soft wrap painter.
      allowing(painter).getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP); will(returnValue(SOFT_WRAP_DRAWING_WIDTH));
    }});

    myMapper = new CachingSoftWrapDataMapper(myEditor, myStorage);
  }
  
  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }

  @Test
  public void noSoftWrapsAndFolding() {
    String document =
      "class Test { \n" +
      "  public void foo() {}  \n" +
      "  \n" +
      "}";
    test(document);
  }

  @Test
  public void singleSoftWrap() {
    String document =
      "class Test { \n" +
      "  public void <WRAP>\n" +
      "    </WRAP>foo() {\n" +
      "  }  \n" +
      "  \n" +
      "}";
    test(document);
  }
  
  @Test
  public void multipleSoftWrappedLogicalLines() {
    String document =
      "public class Test {\n" +
      "  public void foo(int[] data) {\n" +
      "    bar(data[0], <WRAP>\n" +
      "       </WRAP>data[1] <WRAP>\n" +
      "       </WRAP>data[2]     \n" +
      "       data[3], \n" +
      "       data[4], <WRAP>\n" +
      "       </WRAP>data[5] <WRAP>\n" +
      "       </WRAP>data[6]);     \n" +
      "  }\n" +
      "  public void bar(int ... i) {\n" +
      "  }\n" +
      "}";
    test(document);
  }

  @Test
  public void singleLineFolding() {
    String document =
      "class Test {\n" +
      "  Map<String, Integer> map = new HashMap<FOLD><String, Integer></FOLD>();  \n" +
      "  \n" +
      "}";
    test(document);
  }

  @Test
  public void twoLinesFolding() {
    String document =
      "class Test {\n" +
      "  public void foo() {<FOLD>\n" +
      "  }</FOLD>\n" +
      "}";
    test(document);
  }

  @Test
  public void threeLinesFolding() {
    String document =
      "class Test {\n" +
      "  public void foo() {<FOLD>\n" +
      "    int i = 1; \n" +
      "  }</FOLD>\n" +
      "}";
    test(document);
  }

  @Test
  public void softWrappedSingleLineFolding() {
    String document =
      "class Test {<WRAP>\n" +
      "  </WRAP><FOLD>public void foo() {}</FOLD>  \n" +
      "  \n" +
      "}";
    test(document);
  }

  @Test
  public void softWrappedMultiLineLineFolding() {
    String document =
      "class Test {<WRAP>\n" +
      "  </WRAP><FOLD>public void foo() {\n" +
      "  }</FOLD>  \n" +
      "}";
    test(document);
  }

  @Test
  public void multipleFoldRegionsAfterSingleSoftWrap() {
    String document =
      "class Test {<WRAP>\n" +
      "  </WRAP><FOLD>public void foo() {\n" +
      "  }</FOLD>  <FOLD>// comment</FOLD>  \n" +
      "}";
    test(document);
  }

  @Test
  public void softWrapAndFoldedLines() {
    String document =
      "public class Test {\n" +
      "  public void foo(int[] data) {\n" +
      "    bar(data[0] <WRAP>\n" +
      "    </WRAP>data[1], <WRAP>\n" +
      "    </WRAP>data[2],  \n" +
      "    data[3],  \n" +
      "    <FOLD>data[4], \n" +
      "    data[5], \n" +
      "    data[6], </FOLD> \n" +
      "    data[7], \n" +
      "    data[8] <WRAP>\n" +
      "    </WRAP>data[9], <WRAP>\n" +
      "    </WRAP>data[10]);  \n" +
      "  }\n" +
      "  public void bar(int ... i) {\n" +
      "  }\n" +
      "}";
    test(document);
  }

  @Test
  public void consecutiveFoldRegions() {
    String document = 
      "package org;\n" +
      "\n" +
      "import <FOLD>java.util.List;\n" +
      "import java.util.ArrayList;\n" +
      "import java.util.LinkedList;</FOLD>\n" +
      "<FOLD>/**\n" +
      " * @author Vasiliy\n" +
      " */</FOLD>\n" +
      "public class Test {\n" +
      "    <FOLD>/**\n" +
      "     * Method-level javadoc\n" +
      "     */</FOLD>\n" +
      "     public void test() {\n" +
      "     }" +
      "}";
    test(document);
  }

  @Test
  public void tabSymbolsBeforeSoftWrap() {
    String document =
      "class Test\t\t{<WRAP>\n" +
      "    </WRAP> \n}";
    test(document);
  }

  @Test
  public void tabSymbolsAfterSoftWrap() {
    String document =
      "class Test {<WRAP>\n" +
      "    </WRAP> \t\t\n" +
      "}";
    test(document);
  }

  @Test
  public void multipleTabsAndSoftWraps() {
    String document =
      "public class \tTest {\n" +
      "  public void foo(int[] data) {\n" +
      "    bar(data[0], data[1],\t\t <WRAP>\n" +
      "       </WRAP>data[2], data[3], <WRAP>\n" +
      "       </WRAP>data[4], data[5],\t \t    \n" +
      "       data[6], data[7],\t \t \n" +
      "       data[8], data[9],\t \t <WRAP>\n" +
      "       </WRAP>data[10], data[11], <WRAP>\n" +
      "       </WRAP>data[12],\t \t data[13]);     \n" +
      "  }\n" +
      "  public void bar(int ... i) {\n" +
      "  }\n" +
      "}";
    test(document);
  }

  @Test
  public void tabBeforeFolding() {
    
    String document =
      "class Test\t \t <FOLD>{\n" +
      "    </FOLD> \t\t\n" +
      "}";
    test(document);
  }

  @Test
  public void multipleTabsAndFolding() {
    String document =
      "public class \tTest {\n" +
      "  public void foo(int[] data) {\n" +
      "    bar(data[0], data[1],\t\t <FOLD>\n" +
      " \t \t      </FOLD>data[2], data[3]\n" +
      "       data[4], data[5],\t \t \n" +
      "       data[6], data[7],\t \t <FOLD>\n" +
      "\t   \t    </FOLD>);     \n" +
      "  }\n" +
      "  public void bar(int ... i) {\n" +
      "  }\n" +
      "}";
    test(document);
  }

  private int getLineNumber(int offset) {
    int line = 0;
    for (TextRange range : myLineRanges) {
      if (offset >= range.getStartOffset() && offset <= range.getEndOffset()) {
        return line;
      }
      line++;
    }
    return line + 1;
  }

  private int getLineStartOffset(int line) {
    checkLine(line);
    return myLineRanges.get(line).getStartOffset();
  }

  private int getLineEndOffset(int line) {
    checkLine(line);
    return myLineRanges.get(line).getEndOffset();
  }

  private void checkLine(int line) {
    if (line < 0 || line >= myLineRanges.size()) {
      throw new AssertionError(String.format("Can't retrieve target data for the given line (%d). Reason - it's not within allowed "
                                             + "bounds ([0; %d])", line, myLineRanges.size() - 1));
    }
  }

  @Nullable
  private SoftWrap getSoftWrap(int offset) {
    return myStorage.getSoftWrap(offset);
  }
  
  @Nullable
  private FoldRegion getCollapsedFoldRegion(int offset) {
    for (FoldRegion region : myFoldRegions) {
      if (region.getStartOffset() <= offset && region.getEndOffset() > offset) {
        return region;
      }
    }
    return null;
  }

  private VisualPosition logicalToVisual(LogicalPosition position) {
    DataEntry dataEntry = myExpectedData.get(findIndex(new DataEntry(null, position, 0, false), LOGICAL_POSITIONS_COMPARATOR));
    return toSoftWrapUnawareVisual(dataEntry);
  }

  private int logicalToOffset(LogicalPosition position) {
    DataEntry dataEntry = myExpectedData.get(findIndex(new DataEntry(null, position, 0, false), LOGICAL_POSITIONS_COMPARATOR));
    return dataEntry.offset;
  }

  private LogicalPosition offsetToLogical(int offset) {
    DataEntry dataEntry = myExpectedData.get(findIndex(new DataEntry(null, null, offset, false), OFFSETS_COMPARATOR));
    return toVisualPositionUnawareLogical(dataEntry);
  }

  private LogicalPosition offsetToSoftWrapUnawareLogical(int offset) {
    DataEntry dataEntry = myExpectedData.get(findIndex(new DataEntry(null, null, offset, false), OFFSETS_COMPARATOR));
    return toVisualPositionUnawareLogical(dataEntry);
  }

  private int findIndex(DataEntry key, Comparator<DataEntry> comparator) {
    int i = Collections.binarySearch(myExpectedData, key, comparator);
    if (i < 0 || i >= myExpectedData.size()) {
      throw new AssertionError(String.format("Can't find pre-configured data entry for the given key (%s). "
                                             + "Available data: %s", key, myExpectedData));
    }
    return i;
  }

  private static VisualPosition toSoftWrapUnawareVisual(DataEntry dataEntry) {
    LogicalPosition logical = dataEntry.logical;
    return new VisualPosition(logical.line - logical.foldedLines, logical.column + logical.foldingColumnDiff);
  }

  private static LogicalPosition toSoftWrapUnawareLogicalByVisual(DataEntry dataEntry) {
    LogicalPosition logical = dataEntry.logical;
    return new LogicalPosition(
      logical.line + logical.softWrapLinesBeforeCurrentLogicalLine + logical.softWrapLinesOnCurrentLogicalLine,
      logical.column
    );
  }

  private static LogicalPosition toVisualPositionUnawareLogical(DataEntry dataEntry) {
    LogicalPosition logical = dataEntry.logical;
    return new LogicalPosition(logical.line, logical.column);
  }

  private void test(String documentText) {
    init(documentText);
    for (DataEntry data : myExpectedData) {

      // Check logical by visual.
      LogicalPosition actualLogicalByVisual = myMapper.visualToLogical(data.visual);
      // We don't want to perform the check for logical positions that correspond to the folded space because all of them relate to
      // the same logical position of the folding start.
      if (!data.foldedSpace && !data.insideTab && !equals(data.logical, actualLogicalByVisual)) {
        throw new AssertionError(
          String.format("Detected unmatched logical position by visual (%s). Expected: '%s', actual: '%s'. Calculation was performed "
                        + "against soft wrap-unaware logical: '%s'",
                        data.visual, data.logical, actualLogicalByVisual, toSoftWrapUnawareLogicalByVisual(data))
        );
      }

      // Check logical by offset.
      LogicalPosition actualLogicalByOffset = myMapper.offsetToLogicalPosition(data.offset);
      // We don't to perform the check for the data that points to soft wrap location here. The reason is that it shares offset
      // with the first document symbol after soft wrap, hence, examination always fails.
      if (!data.virtualSpace && !data.insideTab && !equals(data.logical, actualLogicalByOffset)) {
        throw new AssertionError(
          String.format("Detected unmatched logical position by offset. Expected: '%s', actual: '%s'. Calculation was performed "
                        + "against offset: '%d' and soft wrap-unaware logical: '%s'",
                        data.logical, actualLogicalByOffset, data.offset, toVisualPositionUnawareLogical(data))
        );
      }

      // Check visual by logical.
      //VisualPosition actualVisualByLogical = myMapper.logicalToVisualPosition(toVisualPositionUnawareLogical(data));
      //if (!data.virtualSpace && !actualVisualByLogical.equals(data.visual)) {
      //  throw new AssertionError(
      //    String.format("Detected unmatched visual position by logical. Expected: '%s', actual: '%s'. Calculation was performed "
      //                  + "against logical position: '%s' and soft wrap-unaware visual: '%s'",
      //                  data.visual, actualVisualByLogical, data.logical, toSoftWrapUnawareVisual(data))
      //  );
      //}
    }
  }

  private static boolean equals(LogicalPosition expected, LogicalPosition actual) {
    return expected.equals(actual) && expected.softWrapLinesBeforeCurrentLogicalLine == actual.softWrapLinesBeforeCurrentLogicalLine
           && expected.softWrapLinesOnCurrentLogicalLine == actual.softWrapLinesOnCurrentLogicalLine
           && expected.softWrapColumnDiff == actual.softWrapColumnDiff && expected.foldedLines == actual.foldedLines
           && expected.foldingColumnDiff == actual.foldingColumnDiff;
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private void init(final String documentText) {
    final TestEditorPosition context = new TestEditorPosition();
    myMockery.checking(new Expectations() {{
      allowing(myDocument).getCharsSequence(); will(new CustomAction("getCharsSequence()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myChars;
        }
      });
      allowing(myDocument).getText(); will(new CustomAction("getCharsSequence()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myChars.toString();
        }
      });
      allowing(myDocument).getTextLength(); will(new CustomAction("getTextLength()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myChars.length();
        }
      });
    }});
    for (int i = 0; i < documentText.length(); i++) {
      if (isSoftWrapStart(documentText, i)) {
        context.onSoftWrapStart();
        i += SOFT_WRAP_START_MARKER.length() - 1; // Subtract 1 because 'i' is incremented by 1 on every iteration
        continue;
      }

      if (isSoftWrapEnd(documentText, i)) {
        context.onSoftWrapEnd();
        i += SOFT_WRAP_END_MARKER.length() - 1; // Subtract 1 because 'i' is incremented by 1 on every iteration
        continue;
      }

      if (isFoldingStart(documentText, i)) {
        context.onFoldingStart();
        i += FOLDING_START_MARKER.length() - 1; // Subtract 1 because 'i' is incremented by 1 on every iteration
        continue;
      }

      if (isFoldingEnd(documentText, i)) {
        context.onFoldingEnd();
        i += FOLDING_END_MARKER.length() - 1; // Subtract 1 because 'i' is incremented by 1 on every iteration
        continue;
      }

      char c = documentText.charAt(i);
      context.onNewSymbol(c);
    }

    myLineRanges.add(new TextRange(context.logicalLineStartOffset, myChars.length()));
  }

  private static boolean isSoftWrapStart(String document, int index) {
    return matches(document, index, SOFT_WRAP_START_MARKER);
  }

  private static boolean isSoftWrapEnd(String document, int index) {
    return matches(document, index, SOFT_WRAP_END_MARKER);
  }

  private static boolean isFoldingStart(String document, int index) {
    return matches(document, index, FOLDING_START_MARKER);
  }

  private static boolean isFoldingEnd(String document, int index) {
    return matches(document, index, FOLDING_END_MARKER);
  }

  private static boolean matches(String document, int index, String pattern) {
    if (index + pattern.length() > document.length()) {
      return false;
    }
    return pattern.equals(document.substring(index, index + pattern.length()));
  }

  private static class DataEntry {
    public final VisualPosition visual;
    public final LogicalPosition logical;
    public final int             offset;
    public final boolean         foldedSpace;
    public final boolean         virtualSpace;
    public final boolean         insideTab;

    DataEntry(VisualPosition visual, LogicalPosition logical, int offset, boolean foldedSpace) {
      this(visual, logical, offset, foldedSpace, false, false);
    }

    DataEntry(VisualPosition visual, LogicalPosition logical, int offset, boolean foldedSpace, boolean virtualSpace, boolean insideTab) {
      this.visual = visual;
      this.logical = logical;
      this.offset = offset;
      this.foldedSpace = foldedSpace;
      this.virtualSpace = virtualSpace;
      this.insideTab = insideTab;
    }

    @Override
    public String toString() {
      return "offset: " + offset +  ", logical: " + logical + ", visual: " + visual + ", folded: " + foldedSpace
             + ", virtual: " + virtualSpace;
    }
  }

  private class TestEditorPosition extends EditorPosition {
    private final StringBuilder mySoftWrapBuffer = new StringBuilder();

    EditorPosition lineStartPosition;
    
    boolean insideSoftWrap;
    boolean insideFolding;
    boolean insideTab;
    int     logicalLineStartOffset;
    int     softWrapStartOffset;
    int     softWrapSymbolsOnCurrentVisualLine;
    int     foldingStartOffset;
    int     foldingStartVisualLine;
    int     foldingStartVisualColumn;

    TestEditorPosition() {
      super(myEditor);
      lineStartPosition = clone();
    }

    public void onSoftWrapStart() {
      softWrapStartOffset = offset;
      insideSoftWrap = true;
      myMapper.onVisualLineStart(lineStartPosition);
    }

    public void onSoftWrapEnd() {
      myStorage.storeOrReplace(
        new SoftWrapImpl(
          new TextChangeImpl('\n' + mySoftWrapBuffer.toString(), softWrapStartOffset),
          mySoftWrapBuffer.length() + 1/* for 'after soft wrap' drawing */,
          (mySoftWrapBuffer.length() * SPACE_SIZE) + SOFT_WRAP_DRAWING_WIDTH
        ));
      mySoftWrapBuffer.setLength(0);
      insideSoftWrap = false;
      x += SOFT_WRAP_DRAWING_WIDTH;
    }

    public void onFoldingStart() {
      foldingStartOffset = offset;
      foldingStartVisualLine = visualLine;
      foldingStartVisualColumn = visualColumn;
      insideFolding = true;
      myMapper.onVisualLineStart(lineStartPosition);
    }

    public void onFoldingEnd() {
      visualColumn += 3; // For '...' folding
      foldingColumnDiff += 3;

      int prevX = x;
      x += 3 * SPACE_SIZE;
      insideFolding = false;
      MockFoldRegion foldRegion = new MockFoldRegion(foldingStartOffset, offset);
      myFoldRegions.add(foldRegion);
      myMapper.onCollapsedFoldRegion(foldRegion, myRepresentationHelper.toVisualColumnSymbolsNumber(foldingStartOffset, offset, prevX), foldingStartVisualLine);
    }

    public void onNewSymbol(char c) {
      symbol = c;
      addData();
      if (insideFolding) {
        myFoldedOffsets.add(offset);
        onNonSoftWrapSymbol(c);
        if (c == '\n') {
          foldedLines++;
          offset++;
          x = 0;
          softWrapColumnDiff = 0;
          softWrapLinesBefore += softWrapLinesCurrent;
          softWrapLinesCurrent = 0;
          foldingColumnDiff = foldingStartVisualColumn;
        }
        else if (c == '\t') {
          int tabWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(c, x);
          x += myRepresentationHelper.charWidth(c, x);

          // There is a possible case that single tabulation symbols is shown in more than one visual column at IJ editor.
          // We store data entry only for the first tab column without 'inside tab' flag then.
          insideTab = true;
          for (int i = tabWidthInColumns - 1; i > 0; i--) {
            logicalColumn++;
            addData(false);
          }
          insideTab = false;

          logicalColumn++;
          offset++;
          foldingColumnDiff -= tabWidthInColumns;
        } else {
          logicalColumn++;
          offset++;
          x += myRepresentationHelper.charWidth(c, x);
          foldingColumnDiff--;
        }
        return;
      }

      // Symbol inside soft wrap.
      if (insideSoftWrap) {
        if (c == '\n') {

          myMapper.beforeSoftWrapLineFeed(this);
          
          // Emulate the situation when the user works with a virtual space after document line end (add such virtual
          // positions two symbols behind the end).
          visualColumn++;
          softWrapColumnDiff++;
          x += SPACE_SIZE;
          addData(true);

          visualColumn++;
          softWrapColumnDiff++;
          x += SPACE_SIZE;
          addData(true);

          visualLine++;
          x = 0;
          softWrapLinesCurrent++;
          softWrapColumnDiff = -logicalColumn - foldingColumnDiff;
          visualColumn = 0;
          myMapper.afterSoftWrapLineFeed(this);
          visualColumn = 1; // For the column reserved for soft wrap sign.
          softWrapColumnDiff++; // For the column reserved for soft wrap sign.
          softWrapSymbolsOnCurrentVisualLine = 0;
        }
        else {
          mySoftWrapBuffer.append(c);
          visualColumn++;
          softWrapColumnDiff++;
          softWrapSymbolsOnCurrentVisualLine++;
          x += myRepresentationHelper.charWidth(c, x);
        }
        return;
      }

      // Symbol outside soft wrap and folding.
      onNonSoftWrapSymbol(c);
      if (c == '\n') {
        visualLine++;
        visualColumn = 0;
        foldingColumnDiff = 0;
        softWrapColumnDiff = 0;
        x = 0;
        softWrapLinesBefore += softWrapLinesCurrent;
        softWrapLinesCurrent = 0;
        softWrapSymbolsOnCurrentVisualLine = 0;
        foldingColumnDiff = 0;
        offset++;
        lineStartPosition.from(this);
      }
      else if (c == '\t') {
        myMapper.onVisualLineStart(lineStartPosition);
        symbolWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(c, x);
        myMapper.onTabulation(this, symbolWidthInColumns);
        int oldX = x;
        x += myRepresentationHelper.charWidth(c, x);
        symbolWidthInPixels = x - oldX;

        // There is a possible case that single tabulation symbols is shown in more than one visual column at IJ editor.
        // We store data entry only for the first tab column without 'inside tab' flag then.
        insideTab = true;
        for (int i = symbolWidthInColumns - 1; i > 0; i--) {
          visualColumn++;
          logicalColumn++;
          addData(false);
        }
        insideTab = false;

        visualColumn++;
        logicalColumn++;
        offset++;
      }
      else {
        visualColumn++;
        logicalColumn++;
        offset++;
        x += myRepresentationHelper.charWidth(c, x);
      }
    }

    private void onNonSoftWrapSymbol(char c) {
      myChars.append(c);
      if (c == '\n') {
        myLineRanges.add(new TextRange(logicalLineStartOffset, offset));

        // Emulate the situation when the user works with a virtual space after document line end (add such virtual
        // positions two symbols behind the end).
        if (!insideFolding) {
          myMapper.onVisualLineEnd(this);
          visualColumn++;
          logicalColumn++;
          addData(true);
          visualColumn++;
          logicalColumn++;
          addData(true);
        }

        logicalLineStartOffset = offset + 1;
        logicalLine++;
        logicalColumn = 0;
      }
    }

    private void addData() {
      addData(insideSoftWrap);
    }

    private void addData(boolean virtualSpace) {
      myExpectedData.add(new DataEntry(
        buildVisualPosition(), buildLogicalPosition(), offset, insideFolding && offset != foldingStartOffset, virtualSpace, insideTab
      ));
    }
  }
}
