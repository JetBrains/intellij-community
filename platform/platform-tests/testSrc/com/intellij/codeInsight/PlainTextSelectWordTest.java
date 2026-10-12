// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class PlainTextSelectWordTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PlatformTestUtil.getPlatformTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  private void doTest(String ext) {
    SelectWordTestUtil.doTest(codeInsightFixture.get(), testMethodName, ext);
  }

  @Test
  public void testPlainTextSentence() {
    doTest("txt");
  }

  @Test
  public void testPlainTextEmptyLine() {
    doTest("txt");
  }

  @Test
  public void testLeadingWhitespace() {
    doTest("txt");
  }

  @Test
  public void testInnerWhitespace() {
    doTest("txt");
  }
}
