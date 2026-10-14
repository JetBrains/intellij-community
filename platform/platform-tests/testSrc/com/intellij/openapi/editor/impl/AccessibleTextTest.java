// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.CaretStopOptions;
import com.intellij.openapi.editor.actions.CaretStopOptionsTransposed;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.AccessibleText;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class AccessibleTextTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private static final String text = """
      public static void printWelcomeMessage() {
        System.out.println("Hello, World!");
      }
    """;


  @Test
  public void testGetAfterIndexReturnsCorrectWord() {
    myFixture.runTest(() -> {
      int firstWordOffset = 3;
      var stopOptions = CaretStopOptionsTransposed.DEFAULT_WINDOWS.toCaretStopOptions();
      var editor = initEditor(stopOptions, false);
      editor.getCaretModel().moveToOffset(firstWordOffset);
      var accessibleText = editor.getContentComponent().getAccessibleContext().getAccessibleText();
      var expectedWord = "static";
      var actualWord = accessibleText.getAfterIndex(AccessibleText.WORD, firstWordOffset);
      assertEquals(expectedWord, actualWord);
    });
  }

  @Test
  public void testGetBeforeIndexReturnsCorrectWord() {
    myFixture.runTest(() -> {
      int secondWordOffset = 9;
      var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
      var editor = initEditor(stopOptions, false);
      editor.getCaretModel().moveToOffset(secondWordOffset);
      var accessibleText = editor.getContentComponent().getAccessibleContext().getAccessibleText();
      var expectedWord = "public";
      var actualWord = accessibleText.getBeforeIndex(AccessibleText.WORD, secondWordOffset);
      assertEquals(expectedWord, actualWord);
    });
  }


  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsAndCamelCaseWhenMovingForward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
      checkWordsWhenMovingForward(stopOptions, true,
                                  "public", "static", "void", "print", "Welcome", "Message", "(", ")", "{",
                                  "\n", "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "\n", "}");
    });
  }


  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsAndCamelcaseWhenMovingBackward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
      checkWordsWhenMovingBackward(stopOptions, true, "}", "}", "\n", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                   ".", "out",
                                   ".", "System",
                                   "\n", "{", ")", "(", "Message", "Welcome", "print", "void", "static", "public");
    });
  }

  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsWhenMovingForward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
      checkWordsWhenMovingForward(stopOptions, false,
                                  "public", "static", "void", "printWelcomeMessage", "(", ")", "{",
                                  "\n", "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "\n", "}");
    });
  }

  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithDefaultStopOptionsWhenMovingBackward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions();
      checkWordsWhenMovingBackward(stopOptions, false, "}", "}", "\n", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                   ".", "out",
                                   ".", "System",
                                   "\n", "{", ")", "(", "printWelcomeMessage", "void", "static", "public");
    });
  }


  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithUnixDefaultStopOptionsWhenMovingForward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT_UNIX.toCaretStopOptions();
      checkWordsWhenMovingForward(stopOptions, false,
                                  "public", "static", "void", "printWelcomeMessage", "(", ")", "{",
                                  "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "}");
    });
  }

  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithUnixDefaultStopOptionsWhenMovingBackward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT_UNIX.toCaretStopOptions();
      checkWordsWhenMovingBackward(stopOptions, false, "}", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                   ".", "out",
                                   ".", "System",
                                   "{", ")", "(", "printWelcomeMessage", "void", "static", "public");
    });
  }

  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithWindowsDefaultStopOptionsWhenMovingForward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT_WINDOWS.toCaretStopOptions();
      checkWordsWhenMovingForward(stopOptions, false,
                                  "public", "static", "void", "printWelcomeMessage", "(", ")", "{", "\n",
                                  " ", "System", ".", "out", ".", "println", "(", "\"", "Hello", ",", "World", "!\"", ")", ";", "\n",
                                  " ", "}");
    });
  }

  @Test
  public void testGetTextAtIndexReturnsCorrectWordWithWindowsDefaultStopOptionsWhenMovingBackward() {
    myFixture.runTest(() -> {
      var stopOptions = CaretStopOptionsTransposed.DEFAULT_WINDOWS.toCaretStopOptions();
      checkWordsWhenMovingBackward(stopOptions, false, "\n", "}", " ", "\n", ";", ")", "!\"", "World", ",", "Hello", "\"", "(", "println",
                                   ".", "out",
                                   ".", "System", " ",
                                   "\n", "{", ")", "(", "printWelcomeMessage", "void", "static", "public");
    });
  }


  private Editor initEditor(CaretStopOptions stopOptions, boolean isCamel) {
    myFixture.initText(text);

    var editor = myFixture.getEditor();
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
    Runnable textStartAction = () -> myFixture.textStart();
    Runnable nextWordAction = () -> myFixture.nextWord();
    checkWords(textStartAction, stopOptions, isCamel, nextWordAction, expectedWords);
  }

  private void checkWordsWhenMovingBackward(CaretStopOptions stopOptions, boolean isCamel, String... expectedWords) {
    Runnable textEndAction = () -> myFixture.textEnd();
    Runnable previousWordAction = () -> myFixture.previousWord();
    checkWords(textEndAction, stopOptions, isCamel, previousWordAction, expectedWords);
  }
}

