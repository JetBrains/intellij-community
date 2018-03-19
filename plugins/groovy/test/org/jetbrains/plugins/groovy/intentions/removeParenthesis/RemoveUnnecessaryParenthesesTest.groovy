// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.removeParenthesis

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class RemoveUnnecessaryParenthesesTest extends LightCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + "intentions/removeParenth/"

  void testRemoveUnnecessaryParenthesis() {
    doTest()
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.launchAction(assertOneElement(myFixture.filterAvailableIntentions("Remove Unnecessary Parentheses")))
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testNothingInsideClosure() {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    assertEmpty(myFixture.filterAvailableIntentions("Remove Unnecessary Parentheses"))
  }

  void testNamedArgs() {
    doTest()
  }
}
