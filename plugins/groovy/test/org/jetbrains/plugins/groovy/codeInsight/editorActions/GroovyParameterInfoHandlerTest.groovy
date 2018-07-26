// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.editorActions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.EditorHintFixture
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.EdtRule
import org.jetbrains.plugins.groovy.util.FixtureRule
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule

import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import static org.jetbrains.plugins.groovy.util.TestUtils.readInput

@CompileStatic
class GroovyParameterInfoHandlerTest implements ResolveTest {

  public final FixtureRule myFixtureRule = new FixtureRule(GroovyProjectDescriptors.GROOVY_3_0, 'parameterInfo/')
  public final TestName myNameRule = new TestName()
  @Rule
  public final TestRule myRules = RuleChain.outerRule(myNameRule).around(myFixtureRule).around(new EdtRule())

  @Override
  CodeInsightTestFixture getFixture() {
    myFixtureRule.fixture
  }

  @Test
  void 'instance method reference zero params'() {
    testParameterHint()
  }

  @Test
  void 'instance method reference single param'() {
    testParameterHint()
  }

  @Test
  void 'instance method reference default param'() {
    testParameterHint()
  }

  @Test
  void 'instance method reference overloads'() {
    testParameterHint()
  }

  private void testParameterHint() {
    def name = myNameRule.methodName.split(" ")*.capitalize().join('').uncapitalize()
    def testName = name + ".test"
    def input = readInput("$fixture.testDataPath$testName")[0]
    def hint = getParameterHint(input)
    configureByText "$input\n-----\n$hint"
    fixture.checkResultByFile(testName)
  }

  private String getParameterHint(String text) {
    configureByText text
    def myHintFixture = new EditorHintFixture(fixture.testRootDisposable)
    fixture.performEditorAction IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO
    dispatchAllInvocationEvents()
    return myHintFixture.currentHintText
  }
}
