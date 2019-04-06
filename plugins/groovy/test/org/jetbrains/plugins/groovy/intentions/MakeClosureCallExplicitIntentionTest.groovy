// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Test

@CompileStatic
class MakeClosureCallExplicitIntentionTest extends GroovyLatestTest implements BaseTest {

  private void doTest(String before, String after) {
    configureByText before
    def intentions = fixture.filterAvailableIntentions(GroovyIntentionsBundle.message("make.closure.call.explicit.intention.name"))
    if (after == null) {
      assert intentions.isEmpty()
    }
    else {
      assert intentions.size() == 1
      fixture.launchAction(intentions.first())
      fixture.checkResult after
    }
  }

  @Test
  void 'closure local variable'() {
    doTest 'def local = {}; <caret>local()', 'def local = {}; <caret>local.call()'
  }

  @Test
  void 'non-closure local variable'() {
    doTest 'def local = ""; <caret>local()', null
  }

  @Test
  void 'callable local variable'() {
    fixture.addFileToProject 'classes.groovy', 'class Callable { def call() {} }'
    doTest 'def local = new Callable(); <caret>local()', null
  }

  @Test
  void 'closure property'() {
    fixture.addFileToProject 'classes.groovy', 'class A { def prop = {} }'
    doTest 'new A().<caret>prop()', 'new A().<caret>prop.call()'
  }

  @Test
  void 'non-closure property'() {
    fixture.addFileToProject 'classes.groovy', 'class A { def prop = "" }'
    doTest 'new A().<caret>prop()', null
  }

  @Test
  void 'closure method'() {
    doTest 'Closure foo() {}; <caret>foo()', null
  }

  @Test
  void 'closure method call'() {
    doTest 'Closure foo() {}; foo().<caret>call()', null
  }
}
