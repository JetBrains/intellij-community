// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixtureKt.groovyCompletionFixture;

public abstract class GrFunctionalExpressionCompletionTest {
  private final TestFixture<GroovyCompletionFixture> completion;

  protected GrFunctionalExpressionCompletionTest(@NotNull TestFixture<LightJavaProject> project, @NotNull String testDataPath) {
    completion = groovyCompletionFixture(project, TestUtils.getAbsoluteTestDataPath() + testDataPath);
  }

  @Test
  public void testInjectMethodForCollection() {
    completion.get().doBasicTest();
  }

  @Test
  public void testEachMethodForMapWithKeyValue() {
    completion.get().doBasicTest();
  }

  @Test
  public void testEachMethodForList() {
    completion.get().doBasicTest();
  }

  @Test
  public void testClosureDefaultParameterInEachMethod() {
    completion.get().doBasicTest();
  }

  @Test
  public void testEachMethodForEnumRanges() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.completeBasic();
    myFixture.type("\n");
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testEachMethodForMapWithEntry() {
    completion.get().doBasicTest();
  }

  @Test
  public void testEachMethodForRanges() {
    completion.get().doBasicTest();
  }

  @Test
  public void testInjectMethodForArray() {
    completion.get().doBasicTest();
  }

  @Test
  public void testCompletionInEachClosure() {
    completion.get().doHasVariantsTest("intValue", "intdiv");
  }

  @Test
  public void testWithMethod() {
    completion.get().doBasicTest();
  }

  @Test
  public void testInjectMethodForMap() {
    completion.get().doBasicTest();
  }
}
