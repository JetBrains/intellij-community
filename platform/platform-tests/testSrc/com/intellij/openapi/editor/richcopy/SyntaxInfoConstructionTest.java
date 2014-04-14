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
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.ui.JBColor;
import junit.framework.TestCase;

import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 3/27/13 11:11 AM
 */
public class SyntaxInfoConstructionTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testBlockSelection() {
    String text =
      "package org;\n" +
      "\n" +
      "public class TestClass {\n" +
      "\n" +
      "    int field;\n" +
      "\n" +
      "    public int getField() {\n" +
      "        return field;\n" +
      "    }\n" +
      "}";
    init(text);

    int blockSelectionStartOffset = text.indexOf("public int");
    Editor editor = myFixture.getEditor();
    LogicalPosition blockSelectionStartPosition = editor.offsetToLogicalPosition(blockSelectionStartOffset);
    LogicalPosition blockSelectionEndPosition = new LogicalPosition(
      blockSelectionStartPosition.line + 2,
      editor.offsetToLogicalPosition(text.indexOf('{', blockSelectionStartOffset)).column + 1);
    editor.getSelectionModel().setBlockSelection(blockSelectionStartPosition, blockSelectionEndPosition);

    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {\n" +
                     "text=\n" +
                     "\n" +
                     "text=    \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "text=\n" +
                     "\n" +
                     "text=}\n");
  }

  public void testColumnModeBlockSelection() {
    String text =
      "package org;\n" +
      "\n" +
      "public class TestClass {\n" +
      "\n" +
      "    int field;\n" +
      "\n" +
      "    public int getField() {\n" +
      "        return field;\n" +
      "    }\n" +
      "}";
    init(text);

    int blockSelectionStartOffset = text.indexOf("public int");
    Editor editor = myFixture.getEditor();
    ((EditorEx) editor).setColumnMode(true);
    LogicalPosition blockSelectionStartPosition = editor.offsetToLogicalPosition(blockSelectionStartOffset);
    LogicalPosition blockSelectionEndPosition = new LogicalPosition(
      blockSelectionStartPosition.line + 2,
      editor.offsetToLogicalPosition(text.indexOf('{', blockSelectionStartOffset)).column + 1);
    editor.getSelectionModel().setBlockSelection(blockSelectionStartPosition, blockSelectionEndPosition);

    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {\n" +
                     "text=\n" +
                     "\n" +
                     "text=    \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "text=\n" +
                     "\n" +
                     "text=}\n");
  }

  public void testColumnModeBlockSelectionWithGaps() {
    String text =
      "public class TestClass {\n" +
      "\n" +
      "    int field;\n" +
      "\n" +
      "    int otherField;\n" +
      "}";
    init(text);

    int blockSelectionStartOffset = text.indexOf("int");
    Editor editor = myFixture.getEditor();
    ((EditorEx) editor).setColumnMode(true);
    LogicalPosition blockSelectionStartPosition = editor.offsetToLogicalPosition(blockSelectionStartOffset);
    LogicalPosition blockSelectionEndPosition = new LogicalPosition(blockSelectionStartPosition.line + 2, blockSelectionStartPosition.column + 16);
    editor.getSelectionModel().setBlockSelection(blockSelectionStartPosition, blockSelectionEndPosition);

    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "text=\n" +
                     "\n" +
                     "text=\n" +
                     "\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=otherField\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n");
  }

  public void testRegularSelection() {
    // We want to exclude unnecessary indents from the pasted results.
    String text =
      "package org;\n" +
      "\n" +
      "public class TestClass {\n" +
      "\n" +
      "    int field;\n" +
      "\n" +
      "    public int getField() {\n" +
      "        return field;\n" +
      "    }\n" +
      "}";
    init(text);

    int selectionStart = text.indexOf("public int");
    int selectionEnd = text.indexOf('}', selectionStart) + 1;
    SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
    selectionModel.setSelection(selectionStart, selectionEnd);

    String expected = "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int \n" +
                      "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {\n" +
                      "\n" +
                      "text=    \n" +
                      "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return \n" +
                      "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                      "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                      "\n" +
                      "text=}\n";

    TestCase.assertEquals(expected, getSyntaxInfo());
    
    selectionModel.setSelection(selectionStart - 2, selectionEnd);
    TestCase.assertEquals(expected, getSyntaxInfo());

    selectionModel.setSelection(selectionStart - 4, selectionEnd);
    TestCase.assertEquals(expected, getSyntaxInfo());
  }

  public void testIncorrectFirstLineCalculationOffset() {
    init("\"tr\" #> <selection>template.statusList.sortBy</selection>(_.index).map(fromStatus =>");

    verifySyntaxInfo("fontStyle=0,text=template.statusList.sortBy\n");
  }

  public void testJavadoc() {
    init(
      "package org;\n" +
      "\n" +
      "import java.io.Serializable;\n" +
      "\n" +
      "<selection>/**\n" +
      " * Code in <code>here</code>\n" +
      " * <strong>Hi</strong> man\n" +
      " * @param <T>\n" +
      " </selection>*/\n" +
      "public interface SampleTest<T> extends Serializable {\n" +
      "    boolean isNotNull();\n" +
      "    T getValue();\n" +
      "}"
    );
    verifySyntaxInfo("foreground=java.awt.Color[r=128,g=128,b=128],fontStyle=2,text=/**\n" +
                     "\n" +
                     "text= * Code in \n" +
                     "background=java.awt.Color[r=226,g=255,b=226],text=<code>\n" +
                     "background=java.awt.Color[r=255,g=255,b=255],text=here\n" +
                     "background=java.awt.Color[r=226,g=255,b=226],text=</code>\n" +
                     "background=java.awt.Color[r=255,g=255,b=255],text=\n" +
                     "\n" +
                     "text= * \n" +
                     "background=java.awt.Color[r=226,g=255,b=226],text=<strong>\n" +
                     "background=java.awt.Color[r=255,g=255,b=255],text=Hi\n" +
                     "background=java.awt.Color[r=226,g=255,b=226],text=</strong>\n" +
                     "background=java.awt.Color[r=255,g=255,b=255],text= man\n" +
                     "\n" +
                     "text= * \n" +
                     "fontStyle=3,text=@param \n" +
                     "foreground=java.awt.Color[r=61,g=61,b=61],text=<T>\n" +
                     "\n" +
                     "text= \n");
  }

  public void testIndentStrippingWhenFirstLineIsMostIndented() throws Exception {
    init("public class Test {\n" +
         "<selection>  int field;\n" +
         "}</selection>");
    verifySyntaxInfo("text=  \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "\n" +
                     "text=}\n");
  }

  public void testIndentStrippingWhenSelectionEndIsBeforeNonWsCharactersOnTheLine() throws Exception {
    init("public class Test {\n" +
         "<selection>  int field;\n" +
         "</selection>}");
    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "\n");
  }

  private String getSyntaxInfo() {
    final StringBuilder syntaxInfo = new StringBuilder();
    Editor editor = myFixture.getEditor();

    TextWithMarkupProcessor processor = new TextWithMarkupProcessor();
    processor.addBuilder(new TextWithMarkupBuilder() {
      @Override
      public void init(Color defaultForeground, Color defaultBackground, String defaultFontFamily, int fontSize) {
        assertEquals(JBColor.BLACK, defaultForeground);
        assertEquals(JBColor.WHITE, defaultBackground);
        assertEquals(getFontSize(), fontSize);
      }

      @Override
      public boolean isOverflowed() {
        return false;
      }

      @Override
      public void setFontFamily(String fontFamily) {
      }

      @Override
      public void setFontStyle(int fontStyle) {
        syntaxInfo.append("fontStyle=").append(fontStyle).append(',');
      }

      @Override
      public void setForeground(Color foreground) {
        syntaxInfo.append("foreground=").append(foreground).append(',');
      }

      @Override
      public void setBackground(Color background) {
        syntaxInfo.append("background=").append(background).append(',');
      }

      @Override
      public void addTextFragment(CharSequence charSequence, int startOffset, int endOffset) {
        syntaxInfo.append("text=").append(charSequence.subSequence(startOffset, endOffset)).append('\n');
      }

      @Override
      public void complete() {

      }
    });
    SelectionModel selectionModel = editor.getSelectionModel();
    processor.collectTransferableData(myFixture.getFile(), editor, selectionModel.getBlockSelectionStarts(), selectionModel.getBlockSelectionEnds());

    return syntaxInfo.toString();
  }

  private void init(String text) {
    myFixture.configureByText(getTestName(true) + ".java", text);
    myFixture.doHighlighting();
  }

  private void verifySyntaxInfo(String info) {
    assertEquals(info, getSyntaxInfo());
  }

  private int getFontSize() {
    return myFixture.getEditor().getColorsScheme().getEditorFontSize();
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
