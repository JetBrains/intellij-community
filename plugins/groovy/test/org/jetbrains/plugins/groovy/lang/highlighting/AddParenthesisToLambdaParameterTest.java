// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.jetbrains.plugins.groovy.util.Groovy30Test;
import org.junit.Test;

public class AddParenthesisToLambdaParameterTest extends Groovy30Test implements ActionTest {
  private void doTest(String before, String after) {
    doActionTest(GroovyBundle.message("add.parenthesis.to.lambda.parameter.list"), before, after);
  }

  @Test
  public void statementExpression() {
    doTest("<caret>a -> /*mark*/ b", "(a) -> /*mark*/ b");
  }

  @Test
  public void nestedLambda() {
    doTest("a -> <caret>b /*mark*/->  c", "a -> (b) /*mark*/ -> c");
  }

  @Test
  public void multiline() {
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
  }
}
