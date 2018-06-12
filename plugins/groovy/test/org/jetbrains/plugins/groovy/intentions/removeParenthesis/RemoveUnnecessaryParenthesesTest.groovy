// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.removeParenthesis

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class RemoveUnnecessaryParenthesesTest extends LightCodeInsightFixtureTestCase {

  private static final String INTENTION_NAME = GroovyIntentionsBundle.message("remove.parentheses.from.method.call.intention.name")

  final String basePath = TestUtils.testDataPath + "intentions/removeParenth/"

  void testRemoveUnnecessaryParenthesis() {
    doTest()
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.launchAction(assertOneElement(myFixture.filterAvailableIntentions(INTENTION_NAME)))
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testNothingInsideClosure() {
    doTest 'println ({ i<caret>t })'
  }

  void testNamedArgs() {
    doTest()
  }

  void 'test empty argument list'() {
    doTest '<caret>foo()'
  }

  void 'test empty argument list with closure arguments'() {
    doTest '<caret>foo() {} {}', 'foo {} {}'
  }

  void 'test argument list with closure arguments'() {
    doTest '<caret>foo(1) {} {}'
  }

  void 'test regular'() {
    doTest 'foo(42)', 'foo 42'
  }

  void 'test single closure in argument list'() {
    doTest '<caret>foo({})', 'foo {}'
  }

  void 'test closure in argument list'() {
    doTest '<caret>foo({}, 1, 2)'
  }

  void 'test spread argument'() {
    doTest '<caret>foo(3, *[], 5)'
  }

  void 'test slashy argument'() {
    doTest '<caret>foo(/1/)'
  }

  void 'test dollar-slashy argument'() {
    doTest '<caret>foo($/1/$)'
  }

  void 'test initializer'() {
    doTest 'def a = <caret>foo(33)', 'def a = foo 33'
  }

  void 'test rhs of assignment'() {
    doTest 'a = <caret>foo(1)', 'a = foo 1'
  }

  private void doTest(String before, String after = null) {
    myFixture.configureByText "_.groovy", before
    def actions = myFixture.filterAvailableIntentions(INTENTION_NAME)
    if (after == null) {
      assert actions.isEmpty()
    }
    else {
      myFixture.launchAction(assertOneElement(actions))
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
      myFixture.checkResult after
    }
  }
}
