// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class PlainTextEditingTest extends EditingTestBase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  @Test
  public void testAutoWrapOnTypingAtLineEnd() {
    myFixture.runTest(() -> {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(27);
      doTest(PlainTextFileType.INSTANCE, 'o', 'p');
    });
  }

  @Test
  public void testAutoWrapOnTyping_WrapsOnNewlyAddedSymbol_WhenTotalLengthExceedsRightMargin() {
    myFixture.runTest(() -> {
      boolean oldWrapValue = mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
      int oldMarginValue = mySettings.getDefaultRightMargin();
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(10);

      try {
        myFixture.init("qqqq aaaa<caret>", PlainTextFileType.INSTANCE);
        myFixture.type('b');
        myFixture.checkResultByText("qqqq aaaab");
        myFixture.type('c');
        myFixture.checkResultByText("qqqq \naaaabc");
      }
      finally {
        mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldWrapValue;
        mySettings.setDefaultRightMargin(oldMarginValue);
      }
    });
  }

  @Test
  public void testAutoWrapsMerge() {
    myFixture.runTest(() -> {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(14);
      doTest(PlainTextFileType.INSTANCE, '1', ' ', '2', ' ', '3', ' ', '4', ' ', '5');
    });
  }

  @Test
  public void testAvoidAutoWrapForIndentedLine() {
    myFixture.runTest(() -> {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(10);
      doTest(PlainTextFileType.INSTANCE, '1', '2', '3', '4', '5');
    });
  }

  @Test
  public void testAvoidAutoWrapForNonIndentedLine() {
    myFixture.runTest(() -> {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(5);
      doTest(PlainTextFileType.INSTANCE, '1', '2', '3', '4', '5');
    });
  }

  @Test
  public void testCopyPasteWithoutUnnecessaryIndent() {
    myFixture.runTest(() -> {
      CodeInsightSettings.runWithTemporarySettings(settings -> {
        settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK;
        doTest(PlainTextFileType.INSTANCE, () -> {
          // Move caret to the non-zero column.
          myFixture.getEditor().getCaretModel().moveToOffset(3);

          // Select all text.
          myFixture.getEditor().getSelectionModel().setSelection(0, myFixture.getEditor().getDocument().getTextLength());

          // Perform 'copy-paste' action. Expecting to get the same file.
          myFixture.copy();
          myFixture.paste();
        });
        return null;
      });
    });
  }

  @Test
  public void testCamelHumpsSelectionAndDigits() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-63313

      String text =
        """
          class Test {
              String my1FieldWithLongName;
          }""";

      myFixture.init(text, PlainTextFileType.INSTANCE);

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      SelectionModel selectionModel = myFixture.getEditor().getSelectionModel();


      IntList expectedBoundaries = new IntArrayList();
      expectedBoundaries.add(text.indexOf("my"));
      expectedBoundaries.add(text.indexOf("Field"));
      expectedBoundaries.add(text.indexOf("With"));
      expectedBoundaries.add(text.indexOf("Long"));
      expectedBoundaries.add(text.indexOf("Name"));


      boolean camelWords = myFixture.getEditor().getSettings().isCamelWords();
      myFixture.getEditor().getSettings().setCamelWords(true);
      try {
        int selectionStart;
        int selectionEnd = text.indexOf("Name") + 1;
        caretModel.moveToOffset(selectionEnd);

        // Check backward selection.
        for (int i = expectedBoundaries.size() - 1; i >= 0; i--) {
          selectionStart = expectedBoundaries.getInt(i);
          myFixture.moveCaretToPreviousWordWithSelection();
          assertEquals(selectionStart, caretModel.getOffset());
          assertEquals(selectionStart, selectionModel.getSelectionStart());
          assertEquals(selectionEnd, selectionModel.getSelectionEnd());
        }

        // Check forward selection.
        selectionStart = text.indexOf("my") - 1;
        selectionModel.removeSelection();
        caretModel.moveToOffset(selectionStart);
        for (int i = 1; i < expectedBoundaries.size(); i++) {
          selectionEnd = expectedBoundaries.getInt(i);
          myFixture.moveCaretToNextWordWithSelection();
          assertEquals(selectionEnd, caretModel.getOffset());
          assertEquals(selectionStart, selectionModel.getSelectionStart());
          assertEquals(selectionEnd, selectionModel.getSelectionEnd());
        }
      }
      finally {
        myFixture.getEditor().getSettings().setCamelWords(camelWords);
      }
    });
  }

  @Test
  public void testNewLineAboveCurrentAction() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-69728
      String initial =
        """
          class Test {
              public void test() {
                  String s =
                      "test <caret>string";
              }
          }""";

      myFixture.init(initial, PlainTextFileType.INSTANCE);
      myFixture.executeAction("EditorStartNewLineBefore");

      String expected =
        """
          class Test {
              public void test() {
                  String s =

                      "test string";
              }
          }""";

      myFixture.checkResultByText(expected);
    });
  }

  @Test
  public void testHungryBackspace() {
    myFixture.runTest(() -> {
      String text =
        "  a   b     <caret>";
      myFixture.init(text, PlainTextFileType.INSTANCE);

      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("  a   b<caret>");

      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("  a   <caret>");

      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("  a<caret>");

      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("  <caret>");

      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("<caret>");

      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("<caret>");
    });
  }


  @Test
  public void testMoveToPrevWordStartInsideStringLiteral() {
    myFixture.runTest(() -> {
      String text = "one \"two<caret>\"";
      myFixture.init(text, PlainTextFileType.INSTANCE);
      myFixture.executeAction("EditorPreviousWord");
      myFixture.checkResultByText("one \"<caret>two\"");
    });
  }

  @Test
  public void testPrefixAwareEditorWithTabOnNonFirstLine() {
    myFixture.runTest(() -> {
      myFixture.init("", PlainTextFileType.INSTANCE);
      ((EditorEx)myFixture.getEditor()).setPrefixTextAndAttributes("prefix", new TextAttributes());
      myFixture.type("first");
      myFixture.type("\n");
      myFixture.type("\tsecond");
      // Expected that no exception is thrown here
    });
  }

  @Test
  public void testCutFromStickySelection() {
    myFixture.runTest(() -> {
      myFixture.init("abcd", PlainTextFileType.INSTANCE);
      EditorEx editor = (EditorEx)myFixture.getEditor();
      CaretModel caretModel = editor.getCaretModel();
      caretModel.moveToOffset(1);
      myFixture.executeAction("EditorToggleStickySelection");
      caretModel.moveToOffset(3);
      assertEquals("bc", editor.getSelectionModel().getSelectedText());

      myFixture.executeAction("EditorCut");
      myFixture.checkResultByText("ad");
    });
  }


  @Test
  public void testSurroundByQuotesAndStickySelection() {
    myFixture.runTest(() -> {
      myFixture.init("ab<caret>cdef", PlainTextFileType.INSTANCE);
      EditorEx editorEx = (EditorEx)myFixture.getEditor();
      editorEx.setStickySelection(true);
      editorEx.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
      CodeInsightSettings.runWithTemporarySettings(settings -> {
        settings.SURROUND_SELECTION_ON_QUOTE_TYPED = true;
        myFixture.type('\'');
        return null;
      });
      myFixture.checkResultByText("ab'<selection>cd</selection>'ef");
    });
  }

  @Test
  public void testStickySelectionIsCanceledByEscapeAction() {
    myFixture.runTest(() -> {
      myFixture.init("ab<caret>cde", PlainTextFileType.INSTANCE);
      EditorEx editorEx = (EditorEx)myFixture.getEditor();
      editorEx.setStickySelection(true);
      editorEx.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
      SelectionModel selectionModel = editorEx.getSelectionModel();
      assertEquals(2, selectionModel.getSelectionStart());
      assertEquals(4, selectionModel.getSelectionEnd());
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
      assertFalse(selectionModel.hasSelection(), "sticky selection is not dropped after 'escape' action");
      assertFalse(editorEx.isStickySelection());
    });
  }

  @Test
  public void testDifferentHumpsMode() {
    myFixture.runTest(() -> {
      String text = "testVar1 testVar2";
      myFixture.init(text, PlainTextFileType.INSTANCE);

      CaretModel caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(0);
      Document document = myFixture.getEditor().getDocument();
      EditorSettings settings = myFixture.getEditor().getSettings();
      boolean camelModeToRestore = settings.isCamelWords();
      settings.setCamelWords(false);

      try {
        myFixture.executeAction("EditorNextWord");
        assertEquals(text.indexOf(" "), caretModel.getOffset());

        myFixture.executeAction("EditorPreviousWordInDifferentHumpsMode");
        assertEquals(text.indexOf("Var1"), caretModel.getOffset());

        caretModel.moveToOffset(text.length());
        myFixture.executeAction("EditorPreviousWord");
        assertEquals(text.indexOf("testVar2"), caretModel.getOffset());

        myFixture.executeAction("EditorNextWordInDifferentHumpsMode");
        assertEquals(text.indexOf("Var2"), caretModel.getOffset());

        caretModel.moveToOffset(text.length());
        settings.setCamelWords(true);
        myFixture.executeAction("EditorPreviousWordInDifferentHumpsMode");
        assertEquals(text.indexOf("testVar2"), caretModel.getOffset());

        myFixture.executeAction("EditorNextWordInDifferentHumpsMode");
        assertEquals(text.length(), caretModel.getOffset());

        settings.setCamelWords(false);
        myFixture.executeAction("EditorDeleteToWordStartInDifferentHumpsMode");
        assertEquals("testVar1 test", document.getText());

        caretModel.moveToOffset(0);
        myFixture.executeAction("EditorDeleteToWordEndInDifferentHumpsMode");
        assertEquals("Var1 test", document.getText());
      }
      finally {
        settings.setCamelWords(camelModeToRestore);
      }
    });
  }

  @PerformanceUnitTest
  @Test
  public void testCalculatingLongLinesPositionPerformanceInEditorWithNoTabs() {
    myFixture.runTest(() -> {
      final String longLine = StringUtil.repeatSymbol(' ', 1000000);
      myFixture.configureFromFileText("x.txt", longLine);
      Benchmark.newBenchmark("calcOffset", () -> {
        for (int i = 0; i < 1000; i++) {
          int off = myFixture.getEditor().logicalPositionToOffset(new LogicalPosition(0, longLine.length() - 1));
          assertEquals(longLine.length() - 1, off);
          int col = myFixture.getEditor().offsetToLogicalPosition(longLine.length() - 1).column;
          assertEquals(longLine.length() - 1, col);
        }
      }).runAsStressTest()
        // the body runs in a lambda on the EDT, where start() can't find the test method on the stack
        .start(PlainTextEditingTest.class.getName() + ".testCalculatingLongLinesPositionPerformanceInEditorWithNoTabs - calcOffset");
    });
  }
}
