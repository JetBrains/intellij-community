// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementDownAction;
import com.intellij.codeInsight.editorActions.moveUpDown.MoveStatementUpAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;

/**
 * Move statement up and down checks for a JUnit 5 test.
 * This is the JUnit 5 replacement of {@link MoveStatementUpDownTestBase}.
 */
@TestOnly
public final class MoveStatementUpDownTestUtil {
  private static final String BASE_PATH = "codeInsight/moveStatementAction/";

  private MoveStatementUpDownTestUtil() {
  }

  /**
   * Runs {@link #doTest(CodeInsightTestFixture, String, Runnable, Runnable)} with no tasks.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String testMethodName) {
    doTest(fixture, testMethodName, null, null);
  }

  /**
   * Copies {@code codeInsight/moveStatementAction/<name>.<ext>} from the test data path of {@code fixture} to the source root, and configures it.
   * Then runs the move statement up action and checks the result against {@code <name>_afterUp.<ext>}.
   * Then copies and configures the same file again, runs the move statement down action, and checks the result against {@code <name>_afterDown.<ext>}.
   * When an {@code after} file is absent, the test expects the unchanged file.
   * The test method name gives {@code name}. For example, {@code testIfStatement} gives {@code ifStatement}.
   * The first test data file with this name gives {@code ext}.
   * Each action runs in a write command of the project of {@code fixture}, so the postponed formatting runs at the end of the command.
   * The method runs {@code beforeMove} before each action, and {@code afterMove} after each check. Both tasks run on the EDT.
   * At the end, the method drops the temporary code style settings of the project.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull String testMethodName,
                            @Nullable Runnable beforeMove,
                            @Nullable Runnable afterMove) {
    String name = PlatformTestUtil.getTestName(testMethodName, true);
    String baseName = BASE_PATH + name;
    String ext = findExtension(fixture, name);
    String fileName = baseName + "." + ext;

    try {
      performAction(fixture, fileName, new MoveStatementUpAction().getHandler(), baseName + "_afterUp." + ext, beforeMove, afterMove);
      performAction(fixture, fileName, new MoveStatementDownAction().getHandler(), baseName + "_afterDown." + ext, beforeMove, afterMove);
    }
    finally {
      CodeStyleSettingsManager.getInstance(fixture.getProject()).dropTemporarySettings();
    }
  }

  private static void performAction(@NotNull CodeInsightTestFixture fixture,
                                    @NotNull String fileName,
                                    @NotNull EditorActionHandler handler,
                                    @NotNull String afterFileName,
                                    @Nullable Runnable beforeMove,
                                    @Nullable Runnable afterMove) {
    fixture.configureFromExistingVirtualFile(fixture.copyFileToProject(fileName, PathUtil.getFileName(fileName)));
    Editor editor = fixture.getEditor();
    if (!new File(fixture.getTestDataPath(), afterFileName).exists()) afterFileName = fileName;
    EdtTestUtil.runInEdtAndWait(() -> {
      boolean enabled = handler.isEnabled(editor, null, null);
      if (beforeMove != null) {
        beforeMove.run();
      }
      if (enabled) {
        WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> handler.execute(editor, null, null));
      }
    });
    fixture.checkResultByFile(afterFileName);
    if (afterMove != null) {
      EdtTestUtil.runInEdtAndWait(afterMove::run);
    }
  }

  private static @NotNull String findExtension(@NotNull CodeInsightTestFixture fixture, @NotNull String name) {
    File[] files = new File(fixture.getTestDataPath(), BASE_PATH).listFiles((dir, fileName) -> StringUtil.startsWithConcatenation(fileName, name, "."));
    return FileTypeManagerEx.getInstanceEx().getExtension(files[0].getName());
  }
}
