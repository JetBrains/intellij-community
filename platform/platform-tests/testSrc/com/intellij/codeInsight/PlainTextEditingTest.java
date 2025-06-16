// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

public class PlainTextEditingTest extends EditingTestBase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testAutoWrapOnTypingAtLineEnd() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(27);
    doTest(PlainTextFileType.INSTANCE, 'o', 'p');
  }

  public void testAutoWrapOnTyping_WrapsOnNewlyAddedSymbol_WhenTotalLengthExceedsRightMargin() {
    boolean oldWrapValue = mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    int oldMarginValue = mySettings.getDefaultRightMargin();
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(10);

    try {
      init("qqqq aaaa<caret>", PlainTextFileType.INSTANCE);
      type('b');
      checkResultByText("qqqq aaaab");
      type('c');
      checkResultByText("qqqq \naaaabc");
    }
    finally {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldWrapValue;
      mySettings.setDefaultRightMargin(oldMarginValue);
    }
  }

  public void testAutoWrapsMerge() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(14);
    doTest(PlainTextFileType.INSTANCE, '1', ' ', '2', ' ', '3', ' ', '4', ' ', '5');
  }

  public void testAvoidAutoWrapForIndentedLine() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(10);
    doTest(PlainTextFileType.INSTANCE, '1', '2', '3', '4', '5');
  }

  public void testAvoidAutoWrapForNonIndentedLine() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(5);
    doTest(PlainTextFileType.INSTANCE, '1', '2', '3', '4', '5');
  }

  public void testCopyPasteWithoutUnnecessaryIndent() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK;
      doTest(PlainTextFileType.INSTANCE, () -> {
        // Move caret to the non-zero column.
        getEditor().getCaretModel().moveToOffset(3);

        // Select all text.
        getEditor().getSelectionModel().setSelection(0, getEditor().getDocument().getTextLength());

        // Perform 'copy-paste' action. Expecting to get the same file.
        copy();
        paste();
      });
      return null;
    });
  }

  public void testCamelHumpsSelectionAndDigits() {
    // Inspired by IDEA-63313

    String text =
      """
        class Test {
            String my1FieldWithLongName;
        }""";

    init(text, PlainTextFileType.INSTANCE);

    CaretModel caretModel = getEditor().getCaretModel();
    SelectionModel selectionModel = getEditor().getSelectionModel();


    IntList expectedBoundaries = new IntArrayList();
    expectedBoundaries.add(text.indexOf("my"));
    expectedBoundaries.add(text.indexOf("Field"));
    expectedBoundaries.add(text.indexOf("With"));
    expectedBoundaries.add(text.indexOf("Long"));
    expectedBoundaries.add(text.indexOf("Name"));


    boolean camelWords = getEditor().getSettings().isCamelWords();
    getEditor().getSettings().setCamelWords(true);
    try {
      int selectionStart;
      int selectionEnd = text.indexOf("Name") + 1;
      caretModel.moveToOffset(selectionEnd);

      // Check backward selection.
      for (int i = expectedBoundaries.size() - 1; i >= 0; i--) {
        selectionStart = expectedBoundaries.getInt(i);
        moveCaretToPreviousWordWithSelection();
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
        moveCaretToNextWordWithSelection();
        assertEquals(selectionEnd, caretModel.getOffset());
        assertEquals(selectionStart, selectionModel.getSelectionStart());
        assertEquals(selectionEnd, selectionModel.getSelectionEnd());
      }
    }
    finally {
      getEditor().getSettings().setCamelWords(camelWords);
    }
  }

  public void testNewLineAboveCurrentAction() {
    // Inspired by IDEA-69728
    String initial =
      """
        class Test {
            public void test() {
                String s =
                    "test <caret>string";
            }
        }""";

    init(initial, PlainTextFileType.INSTANCE);
    executeAction("EditorStartNewLineBefore");

    String expected =
      """
        class Test {
            public void test() {
                String s =

                    "test string";
            }
        }""";

    checkResultByText(expected);
  }

  public void testHungryBackspace() {
    String text =
      "  a   b     <caret>";
    init(text, PlainTextFileType.INSTANCE);

    executeAction("EditorHungryBackSpace");
    checkResultByText("  a   b<caret>");

    executeAction("EditorHungryBackSpace");
    checkResultByText("  a   <caret>");

    executeAction("EditorHungryBackSpace");
    checkResultByText("  a<caret>");

    executeAction("EditorHungryBackSpace");
    checkResultByText("  <caret>");

    executeAction("EditorHungryBackSpace");
    checkResultByText("<caret>");

    executeAction("EditorHungryBackSpace");
    checkResultByText("<caret>");
  }


  public void testMoveToPrevWordStartInsideStringLiteral() {
    String text = "one \"two<caret>\"";
    init(text, PlainTextFileType.INSTANCE);
    executeAction("EditorPreviousWord");
    checkResultByText("one \"<caret>two\"");
  }

  public void testPrefixAwareEditorWithTabOnNonFirstLine() {
    init("", PlainTextFileType.INSTANCE);
    ((EditorEx)getEditor()).setPrefixTextAndAttributes("prefix", new TextAttributes());
    type("first");
    type("\n");
    type("\tsecond");
    // Expected that no exception is thrown here
  }

  public void testCutFromStickySelection() {
    init("abcd", PlainTextFileType.INSTANCE);
    EditorEx editor = (EditorEx)getEditor();
    CaretModel caretModel = editor.getCaretModel();
    caretModel.moveToOffset(1);
    executeAction("EditorToggleStickySelection");
    caretModel.moveToOffset(3);
    assertEquals("bc", editor.getSelectionModel().getSelectedText());

    executeAction("EditorCut");
    checkResultByText("ad");
  }


  public void testSurroundByQuotesAndStickySelection() {
    init("ab<caret>cdef", PlainTextFileType.INSTANCE);
    EditorEx editorEx = (EditorEx)getEditor();
    editorEx.setStickySelection(true);
    editorEx.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      type('\'');
      return null;
    });
    checkResultByText("ab'<selection>cd</selection>'ef");
  }

  public void testStickySelectionIsCanceledByEscapeAction() {
    init("ab<caret>cde", PlainTextFileType.INSTANCE);
    EditorEx editorEx = (EditorEx)getEditor();
    editorEx.setStickySelection(true);
    editorEx.getCaretModel().moveCaretRelatively(2, 0, false, false, false);
    SelectionModel selectionModel = editorEx.getSelectionModel();
    assertEquals(2, selectionModel.getSelectionStart());
    assertEquals(4, selectionModel.getSelectionEnd());
    executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
    assertFalse("sticky selection is not dropped after 'escape' action", selectionModel.hasSelection());
    assertFalse(editorEx.isStickySelection());
  }

  public void testDifferentHumpsMode() {
    String text = "testVar1 testVar2";
    init(text, PlainTextFileType.INSTANCE);

    CaretModel caretModel = getEditor().getCaretModel();
    caretModel.moveToOffset(0);
    Document document = getEditor().getDocument();
    EditorSettings settings = getEditor().getSettings();
    boolean camelModeToRestore = settings.isCamelWords();
    settings.setCamelWords(false);

    try {
      executeAction("EditorNextWord");
      assertEquals(text.indexOf(" "), caretModel.getOffset());

      executeAction("EditorPreviousWordInDifferentHumpsMode");
      assertEquals(text.indexOf("Var1"), caretModel.getOffset());

      caretModel.moveToOffset(text.length());
      executeAction("EditorPreviousWord");
      assertEquals(text.indexOf("testVar2"), caretModel.getOffset());

      executeAction("EditorNextWordInDifferentHumpsMode");
      assertEquals(text.indexOf("Var2"), caretModel.getOffset());

      caretModel.moveToOffset(text.length());
      settings.setCamelWords(true);
      executeAction("EditorPreviousWordInDifferentHumpsMode");
      assertEquals(text.indexOf("testVar2"), caretModel.getOffset());

      executeAction("EditorNextWordInDifferentHumpsMode");
      assertEquals(text.length(), caretModel.getOffset());

      settings.setCamelWords(false);
      executeAction("EditorDeleteToWordStartInDifferentHumpsMode");
      assertEquals("testVar1 test", document.getText());

      caretModel.moveToOffset(0);
      executeAction("EditorDeleteToWordEndInDifferentHumpsMode");
      assertEquals("Var1 test", document.getText());
    }
    finally {
      settings.setCamelWords(camelModeToRestore);
    }
  }

  public void testCalculatingLongLinesPositionPerformanceInEditorWithNoTabs() {
    final String longLine = StringUtil.repeatSymbol(' ', 1000000);
    configureFromFileText("x.txt", longLine);
    Benchmark.newBenchmark("calcOffset", () -> {
      for (int i = 0; i < 1000; i++) {
        int off = getEditor().logicalPositionToOffset(new LogicalPosition(0, longLine.length() - 1));
        assertEquals(longLine.length() - 1, off);
        int col = getEditor().offsetToLogicalPosition(longLine.length() - 1).column;
        assertEquals(longLine.length() - 1, col);
      }
    }).start();
  }
}
