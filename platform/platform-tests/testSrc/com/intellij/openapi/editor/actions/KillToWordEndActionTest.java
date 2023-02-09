// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class KillToWordEndActionTest extends LightPlatformCodeInsightTestCase {

  public void testAtWordStart() {
    doTest(
      "this is a <caret>string",
      "this is a <caret>"
    );
  }

  public void testInTheMiddle() {
    doTest(
      "th<caret>is is a string",
      "th<caret> is a string"
    );
  }

  public void testAtWordEnd() {
    doTest(
      "this is<caret> a string",
      "this is<caret> string"
    );
  }

  public void testAtWhiteSpaceBetweenWords() {
    doTest(
      "this  <caret>     is  a string",
      "this  <caret>  a string"
    );
  }

  public void testAfterLastWordOnTheLineEnd() {
    doTest(
      "this is the first string<caret>     \n" +
      "this is the second string",
      "this is the first string<caret> is the second string"
    );
  }

  public void testAfterLastWord() {
    doTest(
      "this is a string<caret>",
      "this is a string<caret>"
    );
  }

  public void testAfterLastWordBeforeWhiteSpace() {
    doTest(
      "this is a string<caret>  ",
      "this is a string<caret>"
    );
  }

  public void testAtWhiteSpaceAtLineEnd() {
    doTest(
      "this is the first string  <caret>     \n" +
      "this is the second string",
      "this is the first string  <caret> is the second string"
    );
  }

  public void testEscapeChars() {
    configureFromFileText(getTestName(false) + ".java", "class Foo { String s = \"a\\<caret>nb\"; }");
    killToWordEnd();
    checkResultByText("class Foo { String s = \"a\\<caret>b\"; }");
  }

  private void doTest(@NotNull String before, @NotNull String after) {
    configureFromFileText(getTestName(false) + ".txt", before);
    killToWordEnd();
    checkResultByText(after);
  }

  public void testSubsequentKillsInterleavedByCaretMove() throws Exception {
    String text = "<caret>first second third";
    configureFromFileText(getTestName(false) + ".txt", text);
    killToWordEnd();
    checkResultByText(" second third");

    getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, false);
    getEditor().getCaretModel().moveCaretRelatively(-1, 0, false, false, false);
    killToWordEnd();
    Object string = getContents();
    assertEquals(" second", string);
  }

  public void testSubsequentKills() throws Exception {
    String text = "<caret>first second third";
    configureFromFileText(getTestName(false) + ".txt", text);
    killToWordEnd();
    killToWordEnd();
    checkResultByText(" third");

    Object string = getContents();
    assertEquals("first second", string);
  }

  public void testBeforeLastWordAtLine() throws Exception {
    configureFromFileText(getTestName(false) + ".txt", "abc <caret>def\nghi");
    killToWordEnd();
    checkResultByText("abc <caret>\nghi");
    assertEquals("def", getContents());
  }

  public void testBeforeLastWordAtLineWithTrailingSpace() throws Exception {
    configureFromFileText(getTestName(false) + ".txt", "abc <caret>def \nghi");
    killToWordEnd();
    checkResultByText("abc <caret> \nghi");
    assertEquals("def", getContents());
  }

  public void testSubsequentKillsInterruptedBySave() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    cutToLineEnd();
    final FileDocumentManager manager = FileDocumentManager.getInstance();
    manager.saveAllDocuments();
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("public class ParentCopy {\n" +
                      "        public Insets getBorderInsets(    }");

    Object string = getContents();
    assertEquals("Component c) {\n        }\n", string);
  }

  public void testSubsequentKillsWithFolding() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    final FoldingModel model = getEditor().getFoldingModel();
    model.runBatchFoldingOperation(() -> {
      final FoldRegion foldRegion = model.addFoldRegion(70, 90, "");
      assertNotNull(foldRegion);
      foldRegion.setExpanded(false);
      assertFalse(foldRegion.isExpanded());
    });

    cutToLineEnd();
    cutToLineEnd();
    model.runBatchFoldingOperationDoNotCollapseCaret(() -> {
      final FoldRegion[] regions = model.getAllFoldRegions();
      for (FoldRegion region : regions) {
        assertNotNull(region);
        region.setExpanded(true);
      }

    });
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("public class ParentCopy {\n" +
                      "        public Insets getBorderInsets(    }");

    Object string = getContents();
    assertEquals("Component c) {\n        }\n", string);
  }

  public void testDoubleEditors() throws Exception {
    String text = "<caret>first second third";
    configureFromFileText(getTestName(false) + ".txt", text);
    final Document document = getEditor().getDocument();
    EditorFactory editorFactory = EditorFactory.getInstance();
    Editor otherEditor = editorFactory.createEditor(document, getProject());
    try {
      otherEditor.getCaretModel().moveToOffset(document.getTextLength() - 1);
      killToWordEnd();
      killToWordEnd();
      checkResultByText(" third");

      Object string = getContents();
      assertEquals("first second", string);
    }
    finally {
      editorFactory.releaseEditor(otherEditor);
    }
  }


  public void testKillsInterruptedByDelete() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    deleteLine();
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("public class ParentCopy {\n" +
                      "        }");

    Object string = getContents();
    assertEquals("\n" +
                 "    }", string);
  }

  public void testKillsInterruptedByDeleteLine() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    deleteLine();
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("public class ParentCopy {\n" +
                      "        }");

    Object string = getContents();
    assertEquals("\n" +
                 "    }", string);
  }

  public void testKillsInterruptedByDeleteLineEnd() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    executeAction("EditorDeleteToLineEnd");
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("public class ParentCopy {\n" +
                      "        public Insets getBorderInsets(    }");

    Object string = getContents();
    assertEquals("        }\n", string);
  }

  public void testKillsInterruptedByDeleteWord() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    executeAction("EditorDeleteToWordEnd");
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("public class ParentCopy {\n" +
                      "        public Insets getBorderInsets(    }");

    Object string = getContents();
    assertEquals("        }\n", string);
  }

  public void testKillsInterruptedBySplit() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    executeAction("EditorSplitLine");
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("""
                        public class ParentCopy {
                                public Insets getBorderInsets(        }
                            }""");

    Object string = getContents();
    assertEquals("""

                                  \s
                   """, string);
  }

  public void testKillsInterruptedByStartNewLine() throws Exception {
    String text = """
      public class ParentCopy {
              public Insets getBorderInsets(<caret>Component c) {
              }
          }""";
    configureFromFileText(getTestName(false) + ".java", text);
    cutToLineEnd();
    executeAction("EditorStartNewLine");
    cutToLineEnd();
    cutToLineEnd();
    checkResultByText("""
                        public class ParentCopy {
                                public Insets getBorderInsets(
                                       \s
                            }""");

    Object string = getContents();
    assertEquals("\n" +
                 "        }", string);
  }

  private static String getContents() throws UnsupportedFlavorException, IOException {
    Transferable contents = CopyPasteManager.getInstance().getContents();
    assertTrue(contents instanceof KillRingTransferable);
    return (String)contents.getTransferData(DataFlavor.stringFlavor);
  }
}
