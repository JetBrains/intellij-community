// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enter action checks for a JUnit 5 test.
 * This is the replacement of {@link AbstractEnterActionTestCase}.
 */
@TestOnly
public final class EnterActionTestUtil {
  private static final String TEST_PATH = "/codeInsight/enterAction/";

  private EnterActionTestUtil() {
  }

  /**
   * Checks that {@link CodeStyleManager#getLineIndent} at the end of line {@code lineNum} of {@code file} is {@code expected}.
   * Call it from any thread.
   */
  public static void doGetIndentTest(@NotNull Project project, @NotNull PsiFile file, int lineNum, @NotNull String expected) {
    String actual = ReadAction.computeBlocking(() -> {
      Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(file));
      int offset = document.getLineEndOffset(lineNum);
      return CodeStyleManager.getInstance(project).getLineIndent(file, offset);
    });
    assertEquals(expected, actual);
  }

  /**
   * Configures the file {@code a.<ext>} with the text {@code before}, runs the enter action, and checks the result against {@code after}.
   * The texts can hold the caret and selection markers.
   * Call it from any thread.
   */
  public static void doTextTest(@NotNull CodeInsightTestFixture fixture,
                                @NonNls @NotNull String ext,
                                @NonNls @NotNull String before,
                                @NonNls @NotNull String after) {
    fixture.configureByText("a." + ext, before);
    performAction(fixture);
    fixture.checkResult(after, false);
  }

  /**
   * Configures {@code codeInsight/enterAction/<name>.<ext>} from the test data path of {@code fixture}.
   * Then runs the enter action and checks the result against {@code <name>_after.<ext>}.
   * The test method name gives {@code name}. For example, {@code testSimpleEnter} gives {@code SimpleEnter}.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String testMethodName, @NotNull String ext) {
    String name = PlatformTestUtil.getTestName(testMethodName, false);
    fixture.configureByFile(TEST_PATH + name + "." + ext);
    performAction(fixture);
    fixture.checkResultByFile(TEST_PATH + name + "_after." + ext, false);
  }

  /**
   * Runs the enter action in the editor of {@code fixture} in a command, and checks that the action is enabled.
   * Call it from any thread.
   */
  public static void performAction(@NotNull CodeInsightTestFixture fixture) {
    Project project = fixture.getProject();
    Editor editor = fixture.getEditor();
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(
      project, () -> EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ENTER, true), "", null, editor.getDocument()));
  }
}
