// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class AddParenthesisToLambdaParameterTest implements ActionTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_3_0);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  private void doTest(String before, String after) {
    doActionTest(GroovyBundle.message("add.parenthesis.to.lambda.parameter.list"), before, after);
  }

  @Test
  public void statementExpression() {
    runInEdtAndWait(() -> {
      doTest("<caret>a -> /*mark*/ b", "(a) -> /*mark*/ b");
    });
  }

  @Test
  public void nestedLambda() {
    runInEdtAndWait(() -> {
      doTest("a -> <caret>b /*mark*/->  c", "a -> (b) /*mark*/ -> c");
    });
  }

  @Test
  public void multiline() {
    runInEdtAndWait(() -> {
      doTest("""
               true ? a<caret>a -> {
                   //line comment
                   aa++
               } :  () -> {}
               """, """
               true ? (aa) -> {
                   //line comment
                   aa++
               } :  () -> {}
               """);
    });
  }
}
