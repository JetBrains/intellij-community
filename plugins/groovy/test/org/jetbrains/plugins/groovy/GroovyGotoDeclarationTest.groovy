// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class GroovyGotoDeclarationTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + "gotoDeclaration/"

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  private void doTest() {
    def name = getTestName()
    fixture.configureByFile "${name}.groovy"
    fixture.performEditorAction "GotoDeclaration"
    fixture.checkResultByFile("${name}_after.groovy")
  }

  void 'test default constructor'() {
    doTest()
  }

  void 'test qualifier in new'() {
    doTest()
  }
}
