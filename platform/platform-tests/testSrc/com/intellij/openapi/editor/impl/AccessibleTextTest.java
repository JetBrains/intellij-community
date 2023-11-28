// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.CaretStopOptions;
import com.intellij.openapi.editor.actions.CaretStopOptionsTransposed;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;

import javax.accessibility.AccessibleText;

public class AccessibleTextTest extends AbstractEditorTest {
  private static final String text = """
      public static void printWelcomeMessage() {
        System.out.println("Hello, World!");
      }
    """;


  public void testGetAfterIndexReturnsCorrectWord() {
    int firstWordOffset = 3;
    var stopOptions = CaretStopOptionsTransposed.DEFAULT_WINDOWS.toCaretStopOptions();
    var editor = initEditor(stopOptions, false);
    editor.getCaretModel().moveToOffset(firstWordOffset);
    var accessibleText = editor.getContentComponent().getAccessibleContext().getAccessibleText();
    var expectedWord = "static";
    var actualWord = accessibleText.getAfterIndex(AccessibleText.WORD, firstWordOffset);
    assertEquals(expectedWord, actualWord);
  }

  public void testGetBeforeIndexReturnsCorrectWord() {
    int secondWordOffset = 9;
    var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
    var editor = initEditor(stopOptions, false);
    editor.getCaretModel().moveToOffset(secondWordOffset);
    var accessibleText = editor.getContentComponent().getAccessibleContext().getAccessibleText();
    var expectedWord = "public";
    var actualWord = accessibleText.getBeforeIndex(AccessibleText.WORD, secondWordOffset);
    assertEquals(expectedWord, actualWord);
  }


  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsAndCamelCaseWhenMovingForward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
    checkWordsWhenMovingForward(stopOptions, true,
                                "public", "static", "void", "print", "Welcome", "Message", "(", ")", "{",
                                "\n", "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "\n", "}");
  }


  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsAndCamelcaseWhenMovingBackward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
    checkWordsWhenMovingBackward(stopOptions, true, "}", "}", "\n", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                 ".", "out",
                                 ".", "System",
                                 "\n", "{", ")", "(", "Message", "Welcome", "print", "void", "static", "public");
  }

  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsWhenMovingForward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
    checkWordsWhenMovingForward(stopOptions, false,
                                "public", "static", "void", "printWelcomeMessage", "(", ")", "{",
                                "\n", "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "\n", "}");
  }

  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsWhenMovingBackward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
    checkWordsWhenMovingBackward(stopOptions, false, "}", "}", "\n", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                 ".", "out",
                                 ".", "System",
                                 "\n", "{", ")", "(", "printWelcomeMessage", "void", "static", "public");
  }


  public void testGetTextAtIndexReturnsCorrectWordWithUnixDefaultStopOptionsWhenMovingForward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT_UNIX.toCaretStopOptions();
    checkWordsWhenMovingForward(stopOptions, false,
                                "public", "static", "void", "printWelcomeMessage", "(", ")", "{",
                                "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "}");
  }

  public void testGetTextAtIndexReturnsCorrectWordWithUnixDefaultStopOptionsWhenMovingBackward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT_UNIX.toCaretStopOptions();
    checkWordsWhenMovingBackward(stopOptions, false, "}", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                 ".", "out",
                                 ".", "System",
                                 "{", ")", "(", "printWelcomeMessage", "void", "static", "public");
  }

  public void testGetTextAtIndexReturnsCorrectWordWithWindowsDefaultStopOptionsWhenMovingForward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT_WINDOWS.toCaretStopOptions();
    checkWordsWhenMovingForward(stopOptions, false,
                                "public", "static", "void", "printWelcomeMessage", "(", ")", "{", "\n",
                                " ", "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "\n",
                                " ", "}");
  }

  public void testGetTextAtIndexReturnsCorrectWordWithWindowsDefaultStopOptionsWhenMovingBackward() {
    var stopOptions = CaretStopOptionsTransposed.DEFAULT_WINDOWS.toCaretStopOptions();
    checkWordsWhenMovingBackward(stopOptions, false, "\n", "}", " ", "\n", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                 ".", "out",
                                 ".", "System", " ",
                                 "\n", "{", ")", "(", "printWelcomeMessage", "void", "static", "public");
  }


  private Editor initEditor(CaretStopOptions stopOptions, boolean isCamel) {
    initText(text);

    var editor = getEditor();
    editor.getSettings().setCamelWords(isCamel);
    EditorSettingsExternalizable.getInstance().setCaretStopOptions(stopOptions);
    return editor;
  }

  private static void checkWords(Editor editor, Runnable action, String... expectedWords) {
    var accessibleContext = editor.getContentComponent().getAccessibleContext();
    var accessibleText = accessibleContext.getAccessibleText();
    for (String expectedWord : expectedWords) {
      action.run();
      int offset = editor.getCaretModel().getOffset();
      String actualWord = accessibleText.getAtIndex(AccessibleText.WORD, offset);
      assertEquals(expectedWord, actualWord);
    }
  }

  private void checkWords(Runnable initialAction, CaretStopOptions stopOptions, boolean isCamel, Runnable action, String... expectedWords) {
    Editor editor = initEditor(stopOptions, isCamel);
    initialAction.run();
    checkWords(editor, action, expectedWords);
  }

  private void checkWordsWhenMovingForward(CaretStopOptions stopOptions, boolean isCamel, String... expectedWords) {
    Runnable textStartAction = () -> textStart();
    Runnable nextWordAction = () -> nextWord();
    checkWords(textStartAction, stopOptions, isCamel, nextWordAction, expectedWords);
  }

  private void checkWordsWhenMovingBackward(CaretStopOptions stopOptions, boolean isCamel, String... expectedWords) {
    Runnable textEndAction = () -> textEnd();
    Runnable previousWordAction = () -> previousWord();
    checkWords(textEndAction, stopOptions, isCamel, previousWordAction, expectedWords);
  }
}

