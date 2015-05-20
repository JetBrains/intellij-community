package com.intellij.codeInsight;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestFileType;

import java.io.IOException;

public class BlockSelectionEditingTest extends AbstractEditorTest {
  public BlockSelectionEditingTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void testBlockRemovalAndCollapsedFoldRegionsBefore() throws IOException {
    // Inspired by IDEA-69371
    String initialText =
      "fold line #1\n" +
      "fold line #2\n" +
      "initialText    line 1\n" +
      "initialText    line 2\n" +
      "initialText    line 3";

    init(initialText, TestFileType.TEXT);
    final int foldEndOffset = initialText.indexOf("initialText");
    addCollapsedFoldRegion(0, foldEndOffset, "...");
    int column = "initialText".length();
    final LogicalPosition blockStart = new LogicalPosition(3, column);
    final LogicalPosition blockEnd = new LogicalPosition(4, column);
    final SelectionModel selectionModel = myEditor.getSelectionModel();
    selectionModel.setBlockSelection(blockStart, blockEnd);
    delete();
    delete();

    String expectedText =
      "fold line #1\n" +
      "fold line #2\n" +
      "initialText    line 1\n" +
      "initialText  line 2\n" +
      "initialText  line 3";
    assertEquals(expectedText, myEditor.getDocument().getText());
    assertSelectionRanges(new int[][]{{59, 59}, {79, 79}});
    final FoldRegion foldRegion = getFoldRegion(0);
    assertNotNull(foldRegion);
    assertEquals(0, foldRegion.getStartOffset());
    assertEquals(foldEndOffset, foldRegion.getEndOffset());
  }

  public void testBlockSelectionAndCollapsedFolding() throws IOException {
    String text =
      "class Test {\n" +
      "    private class Inner1 {\n" +
      "        int i;\n" +
      "    }\n" +
      "\n" +
      "    private class Inner2 {\n" +
      "        int i;\n" +
      "    }\n" +
      "}";
    init(text, TestFileType.JAVA);

    int foldStart1 = text.indexOf("Inner1");
    foldStart1 = text.indexOf('{', foldStart1);
    int foldEnd1 = text.indexOf('}', foldStart1) + 1;
    addCollapsedFoldRegion(foldStart1, foldEnd1, "...");

    int foldStart2 = text.indexOf('{', foldEnd1);
    int foldEnd2 = text.indexOf('}', foldStart2) + 1;
    addCollapsedFoldRegion(foldStart2, foldEnd2, "...");

    LogicalPosition blockStart = new LogicalPosition(1, 4);
    LogicalPosition blockEnd = new LogicalPosition(5, 5 + "private".length());
    getEditor().getSelectionModel().setBlockSelection(blockStart, blockEnd);

    assertTrue(getFoldRegion(foldStart1).isExpanded());
    assertFalse(getFoldRegion(foldStart2).isExpanded());
  }
}
