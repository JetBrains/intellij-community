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
package com.intellij.codeInsight;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;

public class BlockSelectionEditingTest extends AbstractEditorTest {

  public void testBlockRemovalAndCollapsedFoldRegionsBefore() {
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

  public void testBlockSelectionAndCollapsedFolding() {
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
