// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.util.ActionTest
import org.jetbrains.plugins.groovy.util.Groovy30Test
import org.junit.Test

@CompileStatic
class AddParenthesisToLambdaParameterTest extends Groovy30Test implements ActionTest {

  private void doTest(String before, String after) {
    doActionTest(GroovyBundle.message("add.parenthesis.to.lambda.parameter.list"), before, after)
  }

  @Test
  void 'statement expression'() {
    doTest '<caret>a -> /*mark*/ b', '(a) -> /*mark*/ b'
  }

  @Test
  void 'nested lambda'() {
    doTest 'a -> <caret>b /*mark*/->  c', 'a -> (b) /*mark*/ -> c'
  }

  @Test
  void 'multyline'() {
    doTest '''
true ? a<caret>a -> {
    //line comment
    aa++
} :  () -> {}
''', '''
true ? (aa) -> {
    //line comment
    aa++
} :  () -> {}
'''
  }
}
