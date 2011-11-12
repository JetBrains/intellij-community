/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.AbstractEditorProcessingOnDocumentModificationTest;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.TestFileType;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 09/16/2010
 */
public class SoftWrapApplianceOnDocumentModificationTest extends AbstractEditorProcessingOnDocumentModificationTest {

  private boolean mySmartHome;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (myEditor == null) {
      return;
    }

    EditorSettings settings = myEditor.getSettings();
    mySmartHome = settings.isSmartHome();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myEditor != null) {
      EditorSettings settings = myEditor.getSettings();
      settings.setUseSoftWraps(false);
      settings.setSmartHome(mySmartHome);
    }
    super.tearDown();
  }

  public void testSoftWrapAdditionOnTyping() throws Exception {
    String text =
      "this is a test string that is expected to end just before right margin<caret>";
    init(800, text);

    int offset = myEditor.getDocument().getTextLength() + 1;
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type(" thisisalongtokenthatisnotexpectedtobebrokenintopartsduringsoftwrapping");
    SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset);
    
    // This test fails randomly at TeamCity. The logging below is intended to help analyzing the problem.
    if (softWrap == null) {
      fail(String.format(
        "Expected soft wrap to be located on offset %d. Actual: %s. Document text: %s",
        offset, getSoftWrapModel().getRegisteredSoftWraps(), myEditor.getDocument().getCharsSequence()
      ));
    }
  }

  public void testLongLineOfIdSymbolsIsNotSoftWrapped() throws Exception {
    String text =
      "abcdefghijklmnopqrstuvwxyz<caret>\n" +
      "123\n" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    init(100, text);
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type('1');
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());

    int offset = myEditor.getDocument().getText().indexOf("\n");
    type(" test");
    assertEquals(1, getSoftWrapModel().getRegisteredSoftWraps().size());
    assertNotNull(getSoftWrapModel().getSoftWrap(offset));
  }

  public void testFoldRegionCollapsing() throws Exception {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}";

    init(300, text);
    final FoldingModel foldingModel = myEditor.getFoldingModel();
    assertEmpty(foldingModel.getAllFoldRegions());

    final int startOffset = text.indexOf('{');
    final int endOffset = text.indexOf('}') + 1;

    VisualPosition foldStartPosition = myEditor.offsetToVisualPosition(startOffset);

    addFoldRegion(startOffset, endOffset, "...");

    final FoldRegion foldRegion = getFoldRegion(startOffset);
    assertNotNull(foldRegion);
    assertTrue(foldRegion.isExpanded());
    toggleFoldRegionState(foldRegion, false);

    // Expecting that all offsets that belong to collapsed fold region point to the region's start.
    assertEquals(foldStartPosition, myEditor.offsetToVisualPosition(startOffset + 5));
  }

  public void testTypingEnterAtDocumentEnd() throws IOException {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}<caret>";

    init(300, text);
    type('\n');
    VisualPosition position = myEditor.getCaretModel().getVisualPosition();
    assertEquals(new VisualPosition(5, 0), position);
  }

  public void testDeleteDocumentTail() throws IOException {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}\n" +
      "abcde";

    init(300, text);
    int offset = text.indexOf("abcde");
    myEditor.getSelectionModel().setSelection(offset, text.length());
    delete();
    assertEquals(new VisualPosition(5, 0), myEditor.getCaretModel().getVisualPosition());
  }

  public void testTypingTabOnLastEmptyLine() throws IOException {
    String text =
      "class Test {\n" +
      "}\n" +
      "<caret>";

    init(300, text);
    type('\t');
    assertEquals(new VisualPosition(2, 4), myEditor.getCaretModel().getVisualPosition());
  }

  public void testTypingThatExceedsRightMarginOnLastSoftWrappedLine() throws IOException {
    String text = 
      "line1\n" +
      "long line<caret>";
    
    init(48, text);

    int softWrapsBefore = getSoftWrapModel().getRegisteredSoftWraps().size();
    assertTrue(softWrapsBefore > 0);
    
    for (int i = 0; i < 10; i++) {
      type(String.valueOf(i));
      assertEquals(softWrapsBefore, getSoftWrapModel().getRegisteredSoftWraps().size());
      assertEquals(myEditor.getDocument().getTextLength(), myEditor.getCaretModel().getOffset());
    }
  }

  public void testTrailingFoldRegionRemoval() throws IOException {
    String text =
      "public class BrokenAlignment {\n" +
      "    @SuppressWarnings({ \"SomeInspectionIWantToIgnore\" })\n" +
      "    public void doSomething(int x, int y) {\n" +
      "    }\n" +
      "}";

    init(700, text);

    int startFoldOffset = text.indexOf('@');
    int endFoldOffset = text.indexOf(')');
    addCollapsedFoldRegion(startFoldOffset, endFoldOffset, "/SomeInspectionIWantToIgnore/");

    int endSelectionOffset = text.lastIndexOf("}\n") + 1;
    myEditor.getSelectionModel().setSelection(startFoldOffset, endSelectionOffset);

    delete();
    // Don't expect any exceptions here.
  }

  public void testTypeNewLastLineAndSymbolOnIt() throws IOException {
    // Inspired by IDEA-59439
    String text = 
      "This is a test document\n" +
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5\n" +
      "line6<caret>";
    
    init(700, text);
    type("\nq");
    assertEquals(new VisualPosition(7, 1), myEditor.offsetToVisualPosition(myEditor.getDocument().getTextLength()));
  }
  
  public void testTrailingSoftWrapOffsetShiftOnTyping() throws IOException {
    // The main idea is to type on a logical line before soft wrap in order to ensure that its offset is correctly shifted back.
    String text = 
      "line1<caret>\n" +
      "second line that is long enough to be soft wrapped";
    init(100, text);

    TIntHashSet offsetsBefore = collectSoftWrapStartOffsets(1);
    assertTrue(!offsetsBefore.isEmpty());
    
    type('2');
    final TIntHashSet offsetsAfter = collectSoftWrapStartOffsets(1);
    assertSame(offsetsBefore.size(), offsetsAfter.size());
    offsetsBefore.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        assertTrue(offsetsAfter.contains(value + 1));
        return true;
      }
    });
  }
  
  public void testSoftWrapAwareMappingAfterLeadingFoldRegionCollapsing() throws IOException {
    String text =
      "line to fold 1\n" +
      "line to fold 2\n" +
      "line to fold 3\n" +
      "ordinary line 1\n" +
      "ordinary line 2\n" +
      "ordinary line 3\n" +
      "ordinary line 4\n" +
      "line that is long enough to be soft wrapped\n" +
      "ordinary line 5\n" +
      "ordinary line 6\n" +
      "ordinary line 7\n" +
      "ordinary line 8\n";
    
    init(200, text);
    LogicalPosition position = myEditor.visualToLogicalPosition(new VisualPosition(8, 0));
    assertSame(7, position.line); // Position from soft-wrapped part of the line
    
    addCollapsedFoldRegion(0, text.indexOf("ordinary line 1") - 1, "...");
    assertSame(7, myEditor.visualToLogicalPosition(new VisualPosition(6, 0)).line); // Check that soft wraps cache is correctly updated
  }
  
  public void testCaretPositionOnFoldRegionExpand() throws IOException {
    // We had a problem that caret preserved its visual position instead of offset. This test checks that.
    
    String text = 
      "/**\n" +
      " * This is a test comment\n" +
      " */\n" +
      "public class Test {\n" +
      "}";
    init(500, text);

    addCollapsedFoldRegion(0, text.indexOf("public") - 1, "/**...*/");
    
    int offset = text.indexOf("class");
    CaretModel caretModel = myEditor.getCaretModel();
    caretModel.moveToOffset(offset);
    assertEquals(offset, caretModel.getOffset());
    assertEquals(1, caretModel.getVisualPosition().line);
    
    toggleFoldRegionState(getFoldRegion(0), true);
    assertEquals(3, caretModel.getVisualPosition().line);
    assertEquals(offset, caretModel.getOffset());
  }
  
  public void testBackspaceAtTheEndOfSoftWrappedLine() throws IOException {
    // There was a problem that removing text from the last document line that was soft-wrapped removed soft wraps as well.
    String text = 
      "This a long string that is expected to be wrapped in more than one visual line<caret>";
    init(150, text);

    List<? extends SoftWrap> softWrapsBeforeModification = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(softWrapsBeforeModification.size() > 0);
    
    backspace();
    assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
  }
  
  public void testRemoveOfAllSymbolsFromLastLine() throws IOException {
    // There was a problem that removing all text from the last document line corrupted soft wraps cache.
    String text =
      "Line1\n" +
      "Long line2 that is expected to be soft-wrapped<caret>";
    init(150, text);

    List<? extends SoftWrap> softWrapsBeforeModification = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(softWrapsBeforeModification.size() > 0);

    int offset = myEditor.getCaretModel().getOffset();
    VisualPosition positionBeforeModification = myEditor.offsetToVisualPosition(offset);
    type("\n123");
    myEditor.getSelectionModel().setSelection(offset + 1, myEditor.getDocument().getTextLength());
    delete();
    assertEquals(softWrapsBeforeModification, getSoftWrapModel().getRegisteredSoftWraps());
    assertEquals(positionBeforeModification, myEditor.offsetToVisualPosition(offset));
  }
  
  public void testTypingBeforeCollapsedFoldRegion() throws IOException {
    // We had a problem that soft wraps cache entries that lay after the changed region were considered to be affected by the change.
    // This test checks that situation.
    String text =
      "\n" +
      "fold line1\n" +
      "fold line2\n" +
      "fold line3\n" +
      "fold line4\n" +
      "normal line1";
    init(150, text);
    
    int afterFoldOffset = text.indexOf("normal line1");
    addCollapsedFoldRegion(text.indexOf("fold line1") + 2, afterFoldOffset - 1, "...");
    VisualPosition beforeModification = myEditor.offsetToVisualPosition(afterFoldOffset);
    
    myEditor.getCaretModel().moveToOffset(0);
    type('a');
    
    assertEquals(beforeModification, myEditor.offsetToVisualPosition(afterFoldOffset + 1));
  }
  
  public void testCollapsedFoldRegionPlaceholderThatExceedsVisibleWidth() throws IOException {
    String text =
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5\n" +
      "this is long line that is expected to be soft-wrapped\n" +
      "line6";
    init(100, text);

    assertTrue(!getSoftWrapModel().getRegisteredSoftWraps().isEmpty());

    int offsetAfterSingleLineFoldRegion = text.indexOf("line2") - 1;
    int lineAfterSingleLineFoldRegion = myEditor.offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line;

    int offsetAfterMultiLineFoldRegion = text.indexOf("this") - 1;
    int lineAfterMultiLineFoldRegion = myEditor.offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line;

    int offsetAfterSoftWrap = text.indexOf("line6") - 1;
    VisualPosition beforePositionAfterSoftWrap = myEditor.offsetToVisualPosition(offsetAfterSoftWrap);
    
    // Add single-line fold region which placeholder text is long enough to exceed visual area width.
    addCollapsedFoldRegion(2, offsetAfterSingleLineFoldRegion, "this is a very long placeholder for the single-line fold region");
    assertEquals(lineAfterSingleLineFoldRegion + 1, myEditor.offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line);
    lineAfterSingleLineFoldRegion++;
    
    assertEquals(lineAfterMultiLineFoldRegion + 1, myEditor.offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line);
    lineAfterMultiLineFoldRegion++;
    
    beforePositionAfterSoftWrap = new VisualPosition(beforePositionAfterSoftWrap.line + 1, beforePositionAfterSoftWrap.column); 
    assertEquals(beforePositionAfterSoftWrap, myEditor.offsetToVisualPosition(offsetAfterSoftWrap));
    
    // Add multi-line fold region which placeholder is also long enough to exceed visual area width.
    addCollapsedFoldRegion(text.indexOf("line2") + 2, offsetAfterMultiLineFoldRegion, "long enough placeholder for multi-line fold region");
    assertEquals(lineAfterSingleLineFoldRegion, myEditor.offsetToVisualPosition(offsetAfterSingleLineFoldRegion).line);
    assertEquals(lineAfterMultiLineFoldRegion - 2, myEditor.offsetToVisualPosition(offsetAfterMultiLineFoldRegion).line);
    beforePositionAfterSoftWrap = new VisualPosition(beforePositionAfterSoftWrap.line - 2, beforePositionAfterSoftWrap.column);
    assertEquals(beforePositionAfterSoftWrap, myEditor.offsetToVisualPosition(offsetAfterSoftWrap));
  }
  
  public void testInsertNewStringAndTypeOnItBeforeFoldRegion() throws IOException {
    // There was incorrect processing of fold regions when document change was performed right before them.
    String text =
      "/**\n" +
      " * comment\n" +
      " */\n" +
      "class Test {\n" +
      "}";
    init(700, text);
    String placeholder = "/**...*/";
    addCollapsedFoldRegion(0, text.indexOf("class") - 1, placeholder);
    
    myEditor.getCaretModel().moveToOffset(0);
    type("\n");
    myEditor.getCaretModel().moveToOffset(0);
    type("import");
    
    // Check that fold region info is still correct.
    int foldStartOffset = myEditor.getDocument().getText().indexOf("/**");
    int foldEndOffset = myEditor.getDocument().getText().indexOf("class") - 1;
    assertEquals(new VisualPosition(1, 0), myEditor.offsetToVisualPosition(foldStartOffset));
    assertEquals(new VisualPosition(1, 0), myEditor.offsetToVisualPosition((foldStartOffset + foldEndOffset) / 2));
    assertEquals(new VisualPosition(1, placeholder.length()), myEditor.offsetToVisualPosition(foldEndOffset));
  }
  
  public void testUpdateFoldRegionDataOnTextRemoveBeforeIt() throws IOException {
    String text =
      "1\n" +
      "/**\n" +
      " * comment\n" +
      " */\n" +
      "class Test {\n" +
      "}";
    init(700, text);
    String placeholder = "/**...*/";
    addCollapsedFoldRegion(2, text.indexOf("class") - 1, placeholder);
    
    myEditor.getCaretModel().moveToOffset(0);
    delete();
    assertEquals(new VisualPosition(1, placeholder.length()), myEditor.offsetToVisualPosition(text.indexOf("class") - 2));
  }
  
  public void testRemoveCollapsedFoldRegionThatStartsLogicalLine() throws IOException {
    // There was a problem that soft wraps cache updated on document modification didn't contain information about removed
    // fold region but fold model still provided cached information about it.
    String text =
      "package org;\n" +
      "\n" +
      "@SuppressWarnings(\"all\")\n" +
      "class Test {\n" +
      "}";
    
    init(700, text);
    int startOffset = text.indexOf("@");
    int endOffset = text.indexOf("class") - 1;
    addCollapsedFoldRegion(startOffset, endOffset, "xxx");
    
    // Delete collapsed fold region that starts logical line.
    myEditor.getSelectionModel().setSelection(startOffset, endOffset);
    delete();
    
    assertEquals(startOffset, myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(2, 0))));
  }
  
  public void testFoldRegionThatStartsAtLineEnd() throws IOException {
    String text =
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5";
    
    init(30, text);
    int start = text.indexOf("line3") - 1;
    addCollapsedFoldRegion(start, text.length(), "...");
    assertEquals(1, getSoftWrapModel().getRegisteredSoftWraps().size());
    assertEquals(start, getSoftWrapModel().getRegisteredSoftWraps().get(0).getStart());
  }
  
  public void testHomeProcessing() throws IOException {
    String text = 
      "class Test {\n" +
      "    public String s = \"this is a long string literal that is expected to be soft-wrapped into multiple visual lines\";\n" +
      "}";
    
    init(250, text);
    myEditor.getCaretModel().moveToOffset(text.indexOf("}") - 1);

    List<? extends SoftWrap> softWraps = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(!softWraps.isEmpty());

    CaretModel caretModel = myEditor.getCaretModel();
    int expectedVisualLine = caretModel.getVisualPosition().line;
    while (!softWraps.isEmpty()) {
      SoftWrap softWrap = softWraps.get(softWraps.size() - 1);
      int caretOffsetBefore = caretModel.getOffset();
      
      home();
      
      // Expecting the caret to be moved at the nearest soft wrap start offset.
      int caretOffset = caretModel.getOffset();
      assertTrue(caretOffset < caretOffsetBefore);
      assertEquals(softWrap.getStart(), caretOffset);
      assertEquals(new VisualPosition(expectedVisualLine, softWrap.getIndentInColumns()), caretModel.getVisualPosition());
      
      // Expected that caret is moved to visual line start when it's located on soft wrap start offset at the moment.
      home();
      assertEquals(softWrap.getStart(), caretModel.getOffset());
      assertEquals(new VisualPosition(expectedVisualLine, 0), caretModel.getVisualPosition());
      
      softWraps.remove(softWraps.size() - 1);
      expectedVisualLine--;
    }
    
    // Expecting caret to be located on the first non-white space symbol of non-soft wrapped line.
    home();
    assertEquals(text.indexOf("public"), caretModel.getOffset());
    assertEquals(new VisualPosition(expectedVisualLine, text.indexOf("public") - text.indexOf("{\n") - 2), caretModel.getVisualPosition());
  }

  public void testEndProcessing() throws IOException {
    String text =
      "class Test {\n" +
      "    public String s = \"this is a long string literal that is expected to be soft-wrapped into multiple visual lines\";   \n" +
      "}";

    init(250, text);
    myEditor.getCaretModel().moveToOffset(text.indexOf("\n") + 1);

    List<? extends SoftWrap> softWraps = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
    assertTrue(!softWraps.isEmpty());

    CaretModel caretModel = myEditor.getCaretModel();
    int expectedVisualLine = caretModel.getVisualPosition().line;
    while (!softWraps.isEmpty()) {
      SoftWrap softWrap = softWraps.get(0);
      int caretOffsetBefore = caretModel.getOffset();

      end();

      // Expecting the caret to be moved at the last non-white space symbol on the current visual line.
      int caretOffset = caretModel.getOffset();
      assertTrue(caretOffset > caretOffsetBefore);
      assertFalse(caretOffset > softWrap.getStart());
      if (caretOffset < softWrap.getStart()) {
        // There is a possible case that there are white space symbols between caret position applied on 'end' processing and
        // soft wrap. Let's check that and emulate one more 'end' typing in order to move caret right before soft wrap.
        for (int i = caretOffset; i < softWrap.getStart(); i++) {
          char c = text.charAt(i);
          assertTrue(c == ' ' || c == '\t');
        }
        caretOffsetBefore = caretOffset;
        end();
        caretOffset = caretModel.getOffset();
        assertTrue(caretOffset > caretOffsetBefore);
      }
      
      assertEquals(softWrap.getStart(), caretOffset);
      assertEquals(
        new VisualPosition(expectedVisualLine, myEditor.offsetToVisualPosition(softWrap.getStart() - 1).column + 1),
        caretModel.getVisualPosition()
      );
      
      softWraps.remove(0);
      expectedVisualLine++;
    }
    
    // Check that caret is placed on a last non-white space symbol on current logical line.
    end();
    int lastNonWhiteSpaceSymbolOffset = text.indexOf("\";") + 2;
    assertEquals(lastNonWhiteSpaceSymbolOffset, caretModel.getOffset());
    assertEquals(myEditor.offsetToVisualPosition(lastNonWhiteSpaceSymbolOffset), caretModel.getVisualPosition());
    assertEquals(expectedVisualLine, caretModel.getVisualPosition().line);
    
    // Check that caret is place to the very end of the logical line.
    end();
    int lastSymbolOffset = myEditor.getDocument().getLineEndOffset(caretModel.getLogicalPosition().line);
    assertEquals(lastSymbolOffset, caretModel.getOffset());
    assertEquals(myEditor.offsetToVisualPosition(lastSymbolOffset), caretModel.getVisualPosition());
    assertEquals(expectedVisualLine, caretModel.getVisualPosition().line);
  }
  
  public void testSoftWrapToHardWrapConversion() throws IOException {
    String text =
      "this is line 1\n" +
      "this is line 2\n" +
      "this is line 3\n" +
      "this is line 4\n" +
      "this is line 5";
    
    init(50, text);
    VisualPosition changePosition = new VisualPosition(1, 0);
    myEditor.getCaretModel().moveToVisualPosition(changePosition);
    
    int logicalLinesBefore = myEditor.offsetToLogicalPosition(text.length()).line;
    int offsetBefore = myEditor.getCaretModel().getOffset();
    
    LogicalPosition logicalPositionBefore = myEditor.visualToLogicalPosition(changePosition);
    assertEquals(1, logicalPositionBefore.softWrapLinesOnCurrentLogicalLine);
    assertTrue(logicalPositionBefore.column > 0);
    
    SoftWrap softWrap = getSoftWrapModel().getSoftWrap(offsetBefore);
    assertNotNull(softWrap);
    
    type('a');

    LogicalPosition logicalPositionAfter = myEditor.visualToLogicalPosition(changePosition);
    assertEquals(new LogicalPosition(1, 0, 0, 0, 0, 0, 0), logicalPositionAfter);
    assertEquals(offsetBefore + softWrap.getText().length() + 1, myEditor.getCaretModel().getOffset());
    assertEquals(logicalLinesBefore + 1, myEditor.offsetToLogicalPosition(text.length()).line);
  }
  
  //public void testPastingInsideSelection() throws IOException {
  //  String text = 
  //    "this is line number 0\n" +
  //    "this is line number 1\n" +
  //    "this is line number 2\n" +
  //    "this is line number 3\n" +
  //    "this is line number 4\n" +
  //    "this is line number 5\n" +
  //    "this is line number 6\n" +
  //    "this is the last line";
  //  
  //  init(100, text);
  //  int lineToSelect = 4;
  //  myEditor.getCaretModel().moveToOffset(text.indexOf("number " + lineToSelect));
  //  Document document = myEditor.getDocument();
  //
  //  int startOffset = document.getLineStartOffset(lineToSelect);
  //  int endOffset = document.getLineEndOffset(lineToSelect);
  //  myEditor.getSelectionModel().setSelection(startOffset, endOffset);
  //  
  //  VisualPosition positionBefore = myEditor.offsetToVisualPosition(document.getLineStartOffset(lineToSelect + 1));
  //  List<SoftWrap> softWrapsBefore = new ArrayList<SoftWrap>(getSoftWrapModel().getRegisteredSoftWraps());
  //  
  //  copy();
  //  paste();
  //  
  //  assertEquals(positionBefore, myEditor.offsetToVisualPosition(document.getLineStartOffset(lineToSelect + 1)));
  //  assertEquals(softWrapsBefore, getSoftWrapModel().getRegisteredSoftWraps());
  //}
  
  public void testRemoveHugeLogicalLineThatLaysBeforeSoftWrappedLines() throws IOException {
    String text =
      "short line\n" +
      "this is a long line that is expected to be soft wrapped into more than one or even two visual lines\n" +
      "1. just a line that is long enough to be soft wrapped\n" +
      "2. just a line that is long enough to be soft wrapped\n" +
      "3. just a line that is long enough to be soft wrapped\n" +
      "4. just a line that is long enough to be soft wrapped";
    
    init(100, text);
    Document document = myEditor.getDocument();
    int start = document.getLineStartOffset(1);
    int end = document.getLineEndOffset(1) + 1;
    int visualLinesToRemove = getSoftWrapModel().getSoftWrapsForLine(1).size() + 1;
    
    List<VisualPosition> positionsBefore = new ArrayList<VisualPosition>();
    for (int i = end; i < text.length(); i++) {
      positionsBefore.add(myEditor.offsetToVisualPosition(i));
    }
    Collections.reverse(positionsBefore);

    myEditor.getSelectionModel().setSelection(start, end);
    delete();
    
    // Check that all remembered positions are just shifted to expected number of visual lines.
    for (int i = start; i < document.getTextLength(); i++) {
      VisualPosition position = positionsBefore.remove(positionsBefore.size() - 1);
      assertEquals(new VisualPosition(position.line - visualLinesToRemove, position.column), myEditor.offsetToVisualPosition(i));
    }
  }
  
  public void testVerticalCaretShiftOnLineComment() throws IOException {
    String text =
      "1. just a line that is long enough to be soft wrapped\n" +
      "2. just a line that is long enough to be soft wrapped\n" +
      "3. just a line that is long enough to be soft wrapped\n" +
      "4. just a line that is long enough to be soft wrapped";
    init(100, text);

    CaretModel caretModel = myEditor.getCaretModel();
    caretModel.moveToOffset(text.indexOf("2.") + 2);
    lineComment();
    
    assertEquals(myEditor.offsetToLogicalPosition(text.indexOf("3.") + 2), caretModel.getLogicalPosition());
  }
  
  private static TIntHashSet collectSoftWrapStartOffsets(int documentLine) {
    TIntHashSet result = new TIntHashSet();
    for (SoftWrap softWrap : myEditor.getSoftWrapModel().getSoftWrapsForLine(documentLine)) {
      result.add(softWrap.getStart());
    }
    return result;
  }
  
  public void testNonSmartHome() throws IOException {
    String text =
      "     this is a string that starts with white space and is long enough to be soft-wrapped\n" +
      " this is a 'prefix' text before collapsed multi-line folding that is long enough to be soft-wrapped first fold line\n" +
      "second fold line";
    init(200, text);
    
    addCollapsedFoldRegion(text.indexOf("first fold line"), text.length(), "...");

    List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
    assertTrue(!softWraps.isEmpty());

    CaretModel caretModel = myEditor.getCaretModel();
    SoftWrap softWrap = softWraps.get(0);
    
    // Test non-smart home
    myEditor.getSettings().setSmartHome(false);
    caretModel.moveToOffset(softWrap.getStart() + 1);
    int visLine = caretModel.getVisualPosition().line;
    home();
    assertEquals(new VisualPosition(visLine, 0), caretModel.getVisualPosition());
    
    caretModel.moveToOffset(text.length());
    home();
    visLine = caretModel.getVisualPosition().line;
    assertEquals(new VisualPosition(visLine, 0), caretModel.getVisualPosition());
  }
  
  public void testFoldRegionsUpdate() throws IOException {
    String text = 
      "import java.util.List;\n" +
      "import java.util.ArrayList;\n" +
      "\n" +
      "class Test {\n" +
      "}";
    init(300, text);
    
    final int foldStartOffset = "import".length() + 1;
    int foldEndOffset = text.indexOf("class") - 2;
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...");
    
    // Simulate addition of the new import that modifies existing fold region.
    myEditor.getDocument().insertString(foldEndOffset, "\nimport java.util.Date;\n");
    final FoldingModel foldingModel = myEditor.getFoldingModel();
    foldingModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        FoldRegion oldFoldRegion = getFoldRegion(foldStartOffset);
        assertNotNull(oldFoldRegion);
        foldingModel.removeFoldRegion(oldFoldRegion);
        
        int newFoldEndOffset = myEditor.getDocument().getText().indexOf("class") - 2;
        FoldRegion newFoldRegion = foldingModel.addFoldRegion(foldStartOffset, newFoldEndOffset, "...");
        assertNotNull(newFoldRegion);
        newFoldRegion.setExpanded(false);
      }
    });
    CodeFoldingManager.getInstance(getProject()).updateFoldRegions(myEditor);
    assertEquals(new VisualPosition(2, 0), myEditor.logicalToVisualPosition(new LogicalPosition(5, 0)));
  }

  public void testModificationOfSoftWrappedFoldRegion() throws IOException {
    String text =
      "import java.util.List;import java.util.ArrayList;import java.util.Collection;import java.util.Collections;\n" +
      "import java.util.LinkedList;\n" +
      "import java.util.Set;\n" +
      "\n" +
      "class Test {\n" +
      "}";
    init(300, text);

    final int foldStartOffset = text.indexOf("java.util.Collections");
    final int foldEndOffset = text.indexOf("class") - 2;
    addCollapsedFoldRegion(foldStartOffset, foldEndOffset, "...");

    int modificationOffset = text.indexOf("java.util.Set");
    myEditor.getDocument().insertString(modificationOffset, "import java.util.HashSet;\n");
    // Used to get StackOverflowError here, hence, no additional checking is performed.
  }

  public void testLongSoftWrappedLineWithNonWrappedEndInTheMiddleOfDocument() throws IOException {
    // Inspired by IDEA-70114
    
    String text = 
      "111\n" +
      "222\n" +
      "33333333 33333333333333333333333333333333\n" +
      "444";
    init(100, text);
    
    assertEquals(new LogicalPosition(3, 0), myEditor.visualToLogicalPosition(new VisualPosition(4, 0)));
  }
  
  public void testDeleteThatEndsOnLineWithMultiLineFoldRegion() throws IOException {
    String text = 
      "111\n" +
      "222\n" +
      "333\n" +
      "444 55\n" +
      "666 77";
    
    init(100, text);

    int foldStart = text.indexOf("5");
    int foldEnd = text.indexOf("7");
    addCollapsedFoldRegion(foldStart, foldEnd, "...");

    int selectionStart = text.indexOf("2");
    int selectionEnd = text.indexOf("4");
    getEditor().getSelectionModel().setSelection(selectionStart, selectionEnd);
    
    delete();
    assertEquals(new VisualPosition(1, 8), getEditor().offsetToVisualPosition(getEditor().getDocument().getTextLength() - 1));
  }

  public void testNoWrapAtFirstNonWsSymbolWithCustomIndent() throws IOException {
    String text = 
      "   1111111111111111111111111111111";
    init(70, text);
    getEditor().getSettings().setCustomSoftWrapIndent(0);
    getEditor().getSettings().setUseCustomSoftWrapIndent(true);
    int textLength = getEditor().getDocument().getTextLength();
    //Trigger soft wraps recalculation.
    assertEquals(new LogicalPosition(0, textLength), myEditor.offsetToLogicalPosition(textLength));
    
    // Don't expect soft wraps to be registered as there is no point in wrapping at the first non-white space symbol position
    // in all cases when soft wrap is located at the left screen edge.
    assertEmpty(getSoftWrapModel().getRegisteredSoftWraps());
  }

  public void testLeadingTabWithShiftedWidth() throws IOException {
    // Inspired by IDEA-76353. The point is that we need to consider cached information about tab symbols width during logical
    // position to offset mapping
    String text = "\t test";
    init(100, text);
    ((EditorImpl)myEditor).setPrefixTextAndAttributes(" ", new TextAttributes());
    myEditor.getCaretModel().moveToOffset(text.length());
  }
  
  private void init(final int visibleWidth, @NotNull String fileText) throws IOException {
    init(visibleWidth, fileText, TestFileType.TEXT);
  }
  
  private void init(final int visibleWidth, @NotNull String fileText, @NotNull TestFileType fileType) throws IOException {
    init(fileText, fileType);
    myEditor.getSettings().setUseSoftWraps(true);
    SoftWrapModelImpl model = (SoftWrapModelImpl)myEditor.getSoftWrapModel();
    model.reinitSettings();

    SoftWrapApplianceManager applianceManager = model.getApplianceManager();
    applianceManager.setWidthProvider(new SoftWrapApplianceManager.VisibleAreaWidthProvider() {
      @Override
      public int getVisibleAreaWidth() {
        return visibleWidth;
      }
    });
    applianceManager.registerSoftWrapIfNecessary();
  }

  private static SoftWrapModelImpl getSoftWrapModel() {
    return (SoftWrapModelImpl)myEditor.getSoftWrapModel();
  }
}
