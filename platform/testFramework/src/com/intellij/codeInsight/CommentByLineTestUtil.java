// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Comment by line checks for a JUnit 5 test.
 * This is the replacement of {@link CommentByLineTestBase}.
 */
@TestOnly
public final class CommentByLineTestUtil {
  private CommentByLineTestUtil() {
  }

  /**
   * Runs the comment by line action once and checks the result.
   * See {@link #doTest(CodeInsightTestFixture, String, int, boolean)}.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String testMethodName) {
    doTest(fixture, testMethodName, 1, false);
  }

  /**
   * Runs {@link #doTest(CodeInsightTestFixture, String, int, boolean)} first without inversion, then with inversion.
   */
  public static void doInvertedTest(@NotNull CodeInsightTestFixture fixture, @NotNull String testMethodName, int count) {
    doTest(fixture, testMethodName, count, false);
    doTest(fixture, testMethodName, count, true);
  }

  /**
   * Configures {@code codeInsight/commentByLine/<lang>/before<suffix>.<lang>} from the test data path of {@code fixture}.
   * Then runs the comment by line action {@code count} times and checks the result against {@code after<suffix>.<lang>}.
   * When {@code invert} is {@code true}, the test starts from the {@code after} file and expects the {@code before} file.
   * The test method name gives {@code lang} and {@code suffix}. For example, {@code testXml_DoubleDash} gives {@code xml} and {@code DoubleDash}.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NotNull String testMethodName, int count, boolean invert) {
    String name = PlatformTestUtil.getTestName(testMethodName, true);
    String lang = name.split("[0-9_]+")[0];
    String suffix = name.substring(lang.length());
    suffix = StringUtil.trimStart(suffix, "_");
    fixture.configureByFile("codeInsight/commentByLine/" + lang + (invert ? "/after" : "/before") + suffix + "." + lang);
    for (int i = 0; i < count; i++) {
      performAction(fixture);
    }
    fixture.checkResultByFile("codeInsight/commentByLine/" + lang + (invert ? "/before" : "/after") + suffix + "." + lang);
  }

  /**
   * Runs the comment by line action in the editor of {@code fixture}, and checks that the action is enabled.
   * Call it from any thread.
   */
  public static void performAction(@NotNull CodeInsightTestFixture fixture) {
    Editor editor = fixture.getEditor();
    EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.executeAction(editor, IdeActions.ACTION_COMMENT_LINE, true));
  }
}
