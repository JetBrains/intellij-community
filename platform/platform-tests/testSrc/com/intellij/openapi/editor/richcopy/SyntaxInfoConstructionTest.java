// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy;

import com.intellij.ide.highlighter.HighlighterFactory;
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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.JBColor;

import java.awt.*;

public class SyntaxInfoConstructionTest extends BasePlatformTestCase {
  public void testBlockSelection() {
    String text =
      """
        package org;

        public class TestClass {

            int field;

            public int getField() {
                return field;
            }
        }""";
    init(text);

    int blockSelectionStartOffset = text.indexOf("public int");
    Editor editor = myFixture.getEditor();
    LogicalPosition blockSelectionStartPosition = editor.offsetToLogicalPosition(blockSelectionStartOffset);
    LogicalPosition blockSelectionEndPosition = new LogicalPosition(
      blockSelectionStartPosition.line + 2,
      editor.offsetToLogicalPosition(text.indexOf('{', blockSelectionStartOffset)).column + 1);
    editor.getSelectionModel().setBlockSelection(blockSelectionStartPosition, blockSelectionEndPosition);

    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int\s
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {
                       text=

                       text=   \s
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;
                       text=

                       text=}
                       """);
  }

  public void testColumnModeBlockSelection() {
    String text =
      """
        package org;

        public class TestClass {

            int field;

            public int getField() {
                return field;
            }
        }""";
    init(text);

    int blockSelectionStartOffset = text.indexOf("public int");
    Editor editor = myFixture.getEditor();
    ((EditorEx) editor).setColumnMode(true);
    LogicalPosition blockSelectionStartPosition = editor.offsetToLogicalPosition(blockSelectionStartOffset);
    LogicalPosition blockSelectionEndPosition = new LogicalPosition(
      blockSelectionStartPosition.line + 2,
      editor.offsetToLogicalPosition(text.indexOf('{', blockSelectionStartOffset)).column + 1);
    editor.getSelectionModel().setBlockSelection(blockSelectionStartPosition, blockSelectionEndPosition);

    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int\s
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {
                       text=

                       text=   \s
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;
                       text=

                       text=}
                       """);
  }

  public void testColumnModeBlockSelectionWithGaps() {
    String text =
      """
        public class TestClass {

            int field;

            int otherField;
        }""";
    init(text);

    int blockSelectionStartOffset = text.indexOf("int");
    Editor editor = myFixture.getEditor();
    ((EditorEx) editor).setColumnMode(true);
    LogicalPosition blockSelectionStartPosition = editor.offsetToLogicalPosition(blockSelectionStartOffset);
    LogicalPosition blockSelectionEndPosition = new LogicalPosition(blockSelectionStartPosition.line + 2, blockSelectionStartPosition.column + 16);
    editor.getSelectionModel().setBlockSelection(blockSelectionStartPosition, blockSelectionEndPosition);

    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;
                       text=

                       text=

                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=otherField
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;
                       """);
  }

  public void testRegularSelection() {
    // We want to exclude unnecessary indents from the pasted results.
    String text =
      """
        package org;

        public class TestClass {

            int field;

            public int getField() {
                return field;
            }
        }""";
    init(text);

    int selectionStart = text.indexOf("public int");
    int selectionEnd = text.indexOf('}', selectionStart) + 1;
    SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();
    selectionModel.setSelection(selectionStart, selectionEnd);

    String expected = """
      foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int\s
      foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {

      text=   \s
      foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return\s
      foreground=java.awt.Color[r=102,g=14,b=122],text=field
      foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;

      text=}
      """;

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
      """
        package org;

        import java.io.Serializable;

        <selection>/**
         * Code in <code>here</code>
         * <strong>Hi</strong> man
         * @param <T>
         </selection>*/
        public interface SampleTest<T> extends Serializable {
            boolean isNotNull();
            T getValue();
        }"""
    );
    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=128,g=128,b=128],fontStyle=2,text=/**

                       text= * Code in\s
                       background=java.awt.Color[r=226,g=255,b=226],text=<code>
                       background=java.awt.Color[r=255,g=255,b=255],text=here
                       background=java.awt.Color[r=226,g=255,b=226],text=</code>
                       background=java.awt.Color[r=255,g=255,b=255],text=

                       text= *\s
                       background=java.awt.Color[r=226,g=255,b=226],text=<strong>
                       background=java.awt.Color[r=255,g=255,b=255],text=Hi
                       background=java.awt.Color[r=226,g=255,b=226],text=</strong>
                       background=java.awt.Color[r=255,g=255,b=255],text= man

                       text= *\s
                       fontStyle=3,text=@param\s
                       foreground=java.awt.Color[r=61,g=61,b=61],text=<T>

                       text=\s
                       """);
  }

  public void testIndentStrippingWhenFirstLineIsMostIndented() {
    init("""
           public class Test {
           <selection>  int field;
           }</selection>""");
    verifySyntaxInfo("""
                       text= \s
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;

                       text=}
                       """);
  }

  public void testIndentStrippingWhenSelectionEndIsBeforeNonWsCharactersOnTheLine() {
    init("""
           public class Test {
           <selection>  int field;
           </selection>}""");
    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=int\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;

                       """);
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

    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int\s
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {

                       text=   \s
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;

                       text=}
                       """);
  }

  public void testSlashRSlashNSeparator() {
    String text = """
      package org;\r
      \r
      public class TestClass {\r
      \r
          int field;\r
      \r
          public int getField() {\r
              return field;\r
          }\r
      }""";
    initWithCustomLineSeparators(text);
    int selectionStart = text.indexOf("public int");
    int selectionEnd = text.indexOf('}', selectionStart);
    myFixture.getEditor().getSelectionModel().setSelection(selectionStart, selectionEnd);

    verifySyntaxInfo("""
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=public int\s
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=getField() {

                       text=   \s
                       foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=return\s
                       foreground=java.awt.Color[r=102,g=14,b=122],text=field
                       foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=;

                       text=}
                       """);
  }
  
  public void testNonPhysicalFile() {
    String fileName = "Test.java";
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, fileType, "class Test {}", 0, false);
    VirtualFile virtualFile = psiFile.getViewProvider().getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    EditorFactory editorFactory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)editorFactory.createViewer(document, getProject());
    editor.setHighlighter(HighlighterFactory.createHighlighter(getProject(), fileType));
    try {
      editor.getSelectionModel().setSelection(0, document.getTextLength());
      String syntaxInfo = getSyntaxInfo(editor, psiFile);
      assertEquals("""
                     foreground=java.awt.Color[r=0,g=0,b=128],fontStyle=1,text=class\s
                     foreground=java.awt.Color[r=0,g=0,b=0],fontStyle=0,text=Test {}
                     """, syntaxInfo);
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
            builder.append("text=").append(text, startOffset, endOffset).append('\n');
          }

          @Override
          public void handleForeground(int foregroundId) {
            Color color = colorRegistry.dataById(foregroundId);
            builder.append("foreground=").append(toCanonicalString(color)).append(',');
          }

          @Override
          public void handleBackground(int backgroundId) {
            Color color = colorRegistry.dataById(backgroundId);
            builder.append("background=").append(toCanonicalString(color)).append(',');
          }

          private static String toCanonicalString(Color color) {
            return new Color(color.getRGB(), true).toString();
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
