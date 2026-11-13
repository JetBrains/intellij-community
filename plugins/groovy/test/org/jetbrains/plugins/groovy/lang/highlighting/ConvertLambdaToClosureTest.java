// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ConvertLambdaToClosureTest implements ActionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String before, String after) {
    runInEdtAndWait(() -> doActionTest(GroovyBundle.message("action.convert.lambda.to.closure"), before, after));
  }

  @Test
  public void noParameters() {
    doTest("foo(/*oh*/ () /*hi*/ <caret>-> /*mark*/ {})", "foo(/*oh*/ { /*hi*/ -> /*mark*/ })");
  }

  @Test
  public void noParametersExpressionBody() {
    doTest("foo(/*oh*/ () /*hi*/ <caret>-> /*mark*/ bar)", "foo(/*oh*/ { /*hi*/ -> /*mark*/ bar })");
  }

  @Test
  public void singleParameter() {
    doTest("foo(/*oh*/ it /*hi*/ <caret>-> /*mark*/ {})", "foo(/*oh*/ { it /*hi*/ -> /*mark*/ })");
  }

  @Test
  public void singleParameterInParentheses() {
    doTest("foo(/*oh*/ (it) /*hi*/ <caret>-> /*mark*/ {})", "foo(/*oh*/ { it /*hi*/ -> /*mark*/ })");
  }

  @Test
  public void complex() {
    doTest("""
             foo(/*oh*/ (int it,
             other = 42,
             def ... boo) /*hi*/ <caret>->
             // line comment wow
               /*mark*/ {})
             """, """
             foo(/*oh*/ { int it,
                          other = 42,
                          def ... boo /*hi*/ ->
             // line comment wow
                 /*mark*/
             })
             """);
  }
}
