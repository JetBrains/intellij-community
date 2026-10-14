// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class PlainTextEnterActionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PlatformTestUtil.getPlatformTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  private void doTest(@NotNull String ext) {
    EnterActionTestUtil.doTest(codeInsightFixture.get(), testMethodName, ext);
  }

  private void doTextTest(@NotNull String ext, @NotNull String before, @NotNull String after) {
    EnterActionTestUtil.doTextTest(codeInsightFixture.get(), ext, before, after);
  }

  @Test
  public void testEnterInTextFile() {
    doTest("txt");
  }

  @Test
  public void testEnterWithSpacesAfterCaret() {
    doTextTest("txt", "abc <caret> ", "abc \n<caret> ");
  }

  @Test
  public void testSCR2236() {
    doTextTest("txt", "//file/text/<caret>text/text", "//file/text/\n" + "<caret>text/text");
  }

  @Test
  public void testUnmatchedBraceAtPlainTextFile() {
    // Inspired by IDEA-64060
    doTextTest(
      "txt",
      "   class Foo {<caret>",
      """
           class Foo {
           <caret>
           }\
        """
    );
  }

  @Test
  public void testInJavaDocLikeStringOfPlainTextFile() {
    // Inspired by IDEA-65108
    doTextTest(
      "txt",
      "Here is a list of bullet points:\n" +
      "* Bullet one.  <caret>Bullet two.",
      """
        Here is a list of bullet points:
        * Bullet one. \s
        <caret>Bullet two."""
    );
  }

  @Test
  public void testIndentCalculationWithTabsInIndent() {
    doTextTest("txt", "\t<caret>\tabc", "\t\n\t<caret>\tabc");
  }
}
