// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.editorActions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.fixtures.EditorHintFixture
import com.intellij.util.ui.UIUtil
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import static org.jetbrains.plugins.groovy.util.TestUtils.readInput

@CompileStatic
class GroovyParameterInfoHandlerTest extends GroovyLatestTest implements ResolveTest {

  GroovyParameterInfoHandlerTest() {
    super("parameterInfo/")
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

  @Test
  void 'method with several named params'() {
    testParameterHint()
  }

  @Test
  void 'method with named params annotation'() {
    testParameterHint()
  }

  private void testParameterHint() {
    def name = testName.split(" ")*.capitalize().join('').uncapitalize() + ".test"
    def input = readInput("$fixture.testDataPath$name")[0]
    def hint = getParameterHint(input)
    configureByText "$input\n-----\n$hint"
    fixture.checkResultByFile(name)
  }

  private String getParameterHint(String text) {
    configureByText text
    def myHintFixture = new EditorHintFixture(fixture.testRootDisposable)
    fixture.performEditorAction IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO
    dispatchAllInvocationEvents()
    waitForParameterInfo()
    return myHintFixture.currentHintText
  }

  static void waitForParameterInfo() {
    // effective there is a chain of 3 nonBlockingRead actions
    for (int i = 0; i < 3; i++) {
      UIUtil.dispatchAllInvocationEvents()
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
  }
}
