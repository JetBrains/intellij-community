// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.jetbrains.plugins.groovy.util.Groovy25Test;
import org.junit.Test;

public class ConvertLambdaToClosureTest extends Groovy25Test implements ActionTest {
  private void doTest(String before, String after) {
    doActionTest(GroovyBundle.message("action.convert.lambda.to.closure"), before, after);
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
