// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

public abstract class GrFunctionalExpressionCompletionTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy;
  private final TestFixture<GroovyCompletionTestFixture> completion;
  private final TestFixture<String> testName = testNameFixture(false);

  protected GrFunctionalExpressionCompletionTest(@NotNull TestFixture<Project> project, @NotNull LightProjectDescriptor descriptor, @NotNull String testDataPath) {
    groovy = groovyFixture(project, descriptor, testDataPath);
    completion = groovyCompletionFixture(groovy);
  }

  @Test
  public void testInjectMethodForCollection() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testEachMethodForMapWithKeyValue() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testEachMethodForList() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testClosureDefaultParameterInEachMethod() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testEachMethodForEnumRanges() {
    runInEdtAndWait(() -> {
      groovy.get().configureByFile(testName.get() + ".groovy");
      groovy.get().completeBasic();
      groovy.get().type("\n");
      groovy.get().checkResultByFile(testName.get() + "_after.groovy");
    });
  }

  @Test
  public void testEachMethodForMapWithEntry() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testEachMethodForRanges() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testInjectMethodForArray() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testCompletionInEachClosure() {
    runInEdtAndWait(() -> {
      completion.get().doHasVariantsTest("intValue", "intdiv");
    });
  }

  @Test
  public void testWithMethod() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }

  @Test
  public void testInjectMethodForMap() { runInEdtAndWait(() -> { completion.get().doBasicTest(); }); }
}
