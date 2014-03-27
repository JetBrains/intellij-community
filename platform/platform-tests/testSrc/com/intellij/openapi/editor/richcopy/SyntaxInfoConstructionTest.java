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
import com.intellij.openapi.editor.richcopy.model.*;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import junit.framework.TestCase;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

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

    verifySyntaxInfo(
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.BOLD), new FontSize(12), new Text(60, 71), // 'public int '
      new Foreground(2), new FontStyle(Font.PLAIN), new Text(71, 83), // 'getField() {'
      new Text('\n'), // '\n'
      new Text(88, 92), // '    ' - indent before 'return field;'
      new Foreground(1), new FontStyle(Font.BOLD), new Text(92, 99), // 'return '
      new Foreground(3), new Text(99, 104), // 'field';
      new Foreground(2), new FontStyle(Font.PLAIN), new Text(104, 105), // ';'
      new Text('\n'), // '\n'
      new Text(110, 111) // '}'
    );
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

    verifySyntaxInfo(
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.BOLD), new FontSize(12), new Text(60, 71), // 'public int '
      new Foreground(2), new FontStyle(Font.PLAIN), new Text(71, 83), // 'getField() {'
      new Text('\n'), // '\n'
      new Text(88, 92), // '    ' - indent before 'return field;'
      new Foreground(1), new FontStyle(Font.BOLD), new Text(92, 99), // 'return '
      new Foreground(3), new Text(99, 104), // 'field';
      new Foreground(2), new FontStyle(Font.PLAIN), new Text(104, 105), // ';'
      new Text('\n'), // '\n'
      new Text(110, 111) // '}'
    );
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

    verifySyntaxInfo(
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.BOLD), new FontSize(12), new Text(30, 34), // 'int '
      new Foreground(2), new Text(34, 39), // 'field'
      new Foreground(3), new FontStyle(Font.PLAIN), new Text(39, 40), // ';'
      new Text('\n'), // '\n'
      new Text('\n'), // '\n'
      new Foreground(1), new FontStyle(Font.BOLD), new Text(46, 50), // 'int '
      new Foreground(2), new Text(50, 60), // 'otherField';
      new Foreground(3), new FontStyle(Font.PLAIN), new Text(60, 61) // ';'
    );
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

    List<OutputInfo> expected = Arrays.asList(
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.BOLD), new FontSize(12), new Text(60, 71), // 'public int '
      new Foreground(2), new FontStyle(Font.PLAIN), new Text(71, 84), // 'getField() {\n'
      new Text(88, 92), // '    ' - indent before 'return field;'
      new Foreground(1), new FontStyle(Font.BOLD), new Text(92, 99), // 'return '
      new Foreground(3), new Text(99, 104), // 'field'
      new Foreground(2), new FontStyle(Font.PLAIN), new Text(104, 106), // ';\n'
      new Text(110, 111) // '}'
    );
    
    TestCase.assertEquals(expected, getSyntaxInfo().getOutputInfos());
    
    selectionModel.setSelection(selectionStart - 2, selectionEnd);
    TestCase.assertEquals(expected, getSyntaxInfo().getOutputInfos());

    selectionModel.setSelection(selectionStart - 4, selectionEnd);
    TestCase.assertEquals(expected, getSyntaxInfo().getOutputInfos());
  }

  public void testIncorrectFirstLineCalculationOffset() {
    init("\"tr\" #> <selection>template.statusList.sortBy</selection>(_.index).map(fromStatus =>");

    verifySyntaxInfo(
      new FontFamilyName(1), new FontStyle(Font.PLAIN), new FontSize(12), new Text(8, 34)
    );
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
    verifySyntaxInfo(
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.ITALIC), new FontSize(12), new Text(44, 48),
      new Text(48, 59),
      new Background(2), new Text(59, 65),
      new Background(3), new Text(65, 69),
      new Background(2), new Text(69, 76),
      new Background(3), new Text(76, 77),
      new Text(77, 80),
      new Background(2), new Text(80, 88),
      new Background(3), new Text(88, 90),
      new Background(2), new Text(90, 99),
      new Background(3), new Text(99, 104),
      new Text(104, 107),
      new FontStyle(Font.BOLD + Font.ITALIC), new Text(107, 114),
      new Foreground(4), new Text(114, 118),
      new Text(118, 119)
    );
  }

  public void testIndentStrippingWhenFirstLineIsMostIndented() throws Exception {
    init("public class Test {\n" +
         "<selection>  int field;\n" +
         "}</selection>");
    verifySyntaxInfo(
      new Text(20, 22), // '  '
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.BOLD), new FontSize(12), new Text(22, 26), // 'int '
      new Foreground(2), new Text(26, 31), // 'field'
      new Foreground(3), new FontStyle(Font.PLAIN), new Text(31, 33), // ';\n'
      new Text(33, 34) // '}'
    );
  }

  public void testIndentStrippingWhenSelectionEndIsBeforeNonWsCharactersOnTheLine() throws Exception {
    init("public class Test {\n" +
         "<selection>  int field;\n" +
         "</selection>}");
    verifySyntaxInfo(
      new Foreground(1), new FontFamilyName(1), new FontStyle(Font.BOLD), new FontSize(12), new Text(22, 26), // 'int '
      new Foreground(2), new Text(26, 31), // 'field'
      new Foreground(3), new FontStyle(Font.PLAIN), new Text(31, 33) // ';\n'
    );
  }

  private SyntaxInfo getSyntaxInfo() {
    final Ref<SyntaxInfo> syntaxInfo = new Ref<SyntaxInfo>();
    Editor editor = myFixture.getEditor();

    TextWithMarkupProcessor processor = new TextWithMarkupProcessor();
    processor.addBuilder(new TextWithMarkupBuilder() {
      @Override
      public void reset() {
      }

      @Override
      public void build(CharSequence charSequence, SyntaxInfo info) {
        syntaxInfo.set(info);
      }
    });
    SelectionModel selectionModel = editor.getSelectionModel();
    processor.collectTransferableData(myFixture.getFile(), editor, selectionModel.getBlockSelectionStarts(), selectionModel.getBlockSelectionEnds());

    return syntaxInfo.get();
  }

  private void init(String text) {
    myFixture.configureByText(getTestName(true) + ".java", text);
    myFixture.doHighlighting();
  }

  private void verifySyntaxInfo(OutputInfo... infos) {
    assertEquals(Arrays.asList(infos), getSyntaxInfo().getOutputInfos());
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
