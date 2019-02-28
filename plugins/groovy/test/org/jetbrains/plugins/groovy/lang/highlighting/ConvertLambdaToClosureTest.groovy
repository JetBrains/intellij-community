// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.util.ActionTest
import org.jetbrains.plugins.groovy.util.Groovy25Test
import org.junit.Test

@CompileStatic
class ConvertLambdaToClosureTest extends Groovy25Test implements ActionTest {

  private void doTest(String before, String after) {
    doActionTest(GroovyBundle.message("action.convert.lambda.to.closure"), before, after)
  }

  @Test
  void 'no parameters'() {
    doTest 'foo(/*oh*/ () /*hi*/ <caret>-> /*mark*/ {})', 'foo(/*oh*/ { /*hi*/ -> /*mark*/ })'
  }

  @Test
  void 'no parameters expression body'() {
    doTest 'foo(/*oh*/ () /*hi*/ <caret>-> /*mark*/ bar)', 'foo(/*oh*/ { /*hi*/ -> /*mark*/ bar })'
  }

  @Test
  void 'single parameter'() {
    doTest 'foo(/*oh*/ it /*hi*/ <caret>-> /*mark*/ {})', 'foo(/*oh*/ { it /*hi*/ -> /*mark*/ })'
  }

  @Test
  void 'single parameter in parentheses'() {
    doTest 'foo(/*oh*/ (it) /*hi*/ <caret>-> /*mark*/ {})', 'foo(/*oh*/ { it /*hi*/ -> /*mark*/ })'
  }

  @Test
  void 'complex'() {
    doTest '''\
foo(/*oh*/ (int it, 
other = 42, 
def ... boo) /*hi*/ <caret>->
// line comment wow
  /*mark*/ {})
''', '''\
foo(/*oh*/ { int it,
             other = 42,
             def ... boo /*hi*/ ->
// line comment wow
    /*mark*/
})
'''
  }
}
