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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.richcopy.model.ColorRegistry;
import com.intellij.openapi.editor.richcopy.model.MarkupHandler;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.ui.JBColor;

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

    verifySyntaxInfo(expected);
    
    selectionModel.setSelection(selectionStart - 2, selectionEnd);
    verifySyntaxInfo(expected);

    selectionModel.setSelection(selectionStart - 4, selectionEnd);
    verifySyntaxInfo(expected);
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

  public void testIndentStrippingWhenFirstLineIsMostIndented() {
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

  public void testIndentStrippingWhenSelectionEndIsBeforeNonWsCharactersOnTheLine() {
    init("public class Test {\n" +
         "<selection>  int field;\n" +
         "</selection>}");
    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "\n");
  }

  public void testSlashRSeparator() {
    String text = "package org;\r" +
                  "\r" +
                  "public class TestClass {\r" +
                  "\r" +
                  "    int field;\r" +
                  "\r" +
                  "    public int getField() {\r" +
                  "        return field;\r" +
                  "    }\r" +
                  "}";
    initWithCustomLineSeparators(text);
    int selectionStart = text.indexOf("public int");
    int selectionEnd = text.indexOf('}', selectionStart);
    myFixture.getEditor().getSelectionModel().setSelection(selectionStart, selectionEnd);

    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {\n" +
                     "\n" +
                     "text=    \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "\n" +
                     "text=}\n");
  }

  public void testSlashRSlashNSeparator() {
    String text = "package org;\r\n" +
                  "\r\n" +
                  "public class TestClass {\r\n" +
                  "\r\n" +
                  "    int field;\r\n" +
                  "\r\n" +
                  "    public int getField() {\r\n" +
                  "        return field;\r\n" +
                  "    }\r\n" +
                  "}";
    initWithCustomLineSeparators(text);
    int selectionStart = text.indexOf("public int");
    int selectionEnd = text.indexOf('}', selectionStart);
    myFixture.getEditor().getSelectionModel().setSelection(selectionStart, selectionEnd);

    verifySyntaxInfo("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {\n" +
                     "\n" +
                     "text=    \n" +
                     "foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return \n" +
                     "foreground=java.awt.Color[r=102,g=14,b=122],text=field\n" +
                     "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;\n" +
                     "\n" +
                     "text=}\n");
  }
  
  public void testNonPhysicalFile() {
    String fileName = "Test.java";
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, fileType, "class Test {}", 0, false);
    VirtualFile virtualFile = psiFile.getViewProvider().getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    EditorFactory editorFactory = EditorFactory.getInstance();
    Editor editor = editorFactory.createViewer(document, getProject());
    try {
      editor.getSelectionModel().setSelection(0, document.getTextLength());
      String syntaxInfo = getSyntaxInfo(editor, psiFile);
      assertEquals("foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=class \n" +
                   "foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=Test {}\n", syntaxInfo);
    }
    finally {
      editorFactory.releaseEditor(editor);
    }
  }

  private String getSyntaxInfo() {
    return getSyntaxInfo(myFixture.getEditor(), myFixture.getFile());
  }
  
  private static String getSyntaxInfo(Editor editor, PsiFile psiFile) {
    final StringBuilder builder = new StringBuilder();
    SelectionModel selectionModel = editor.getSelectionModel();
    String selectedText = selectionModel.getSelectedText(true);
    assertNotNull(selectedText);
    final String text = StringUtil.convertLineSeparators(selectedText);

    TextWithMarkupProcessor processor = new TextWithMarkupProcessor() {
      @Override
      void createResult(SyntaxInfo syntaxInfo, Editor editor) {
        final ColorRegistry colorRegistry = syntaxInfo.getColorRegistry();
        assertEquals(JBColor.BLACK, colorRegistry.dataById(syntaxInfo.getDefaultForeground()));
        assertEquals(JBColor.WHITE, colorRegistry.dataById(syntaxInfo.getDefaultBackground()));
        assertEquals((float)editor.getColorsScheme().getEditorFontSize(), syntaxInfo.getFontSize(), 0.01f);
        syntaxInfo.processOutputInfo(new MarkupHandler() {
          @Override
          public void handleText(int startOffset, int endOffset) {
            builder.append("text=").append(text.substring(startOffset, endOffset)).append('\n');
          }

          @Override
          public void handleForeground(int foregroundId) {
            builder.append("foreground=").append(colorRegistry.dataById(foregroundId)).append(',');
          }

          @Override
          public void handleBackground(int backgroundId) {
            builder.append("background=").append(colorRegistry.dataById(backgroundId)).append(',');
          }

          @Override
          public void handleFont(int fontNameId) {
            assertEquals(1, fontNameId);
          }

          @Override
          public void handleStyle(int style) {
            builder.append("fontStyle=").append(style).append(',');
          }

          @Override
          public boolean canHandleMore() {
            return true;
          }
        });
      }
    };
    processor.collectTransferableData(psiFile, editor, selectionModel.getBlockSelectionStarts(), selectionModel.getBlockSelectionEnds());

    return builder.toString();
  }

  private void init(String text) {
    myFixture.configureByText(getTestName(true) + ".java", text);
    myFixture.doHighlighting();
  }

  private void initWithCustomLineSeparators(final String text) {
    myFixture.configureByText(getTestName(true) + ".java", "");
    final DocumentImpl document = (DocumentImpl)myFixture.getEditor().getDocument();
    document.setAcceptSlashR(true);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
    myFixture.doHighlighting();
  }

  private void verifySyntaxInfo(String info) {
    assertEquals(info, getSyntaxInfo());
  }
}
