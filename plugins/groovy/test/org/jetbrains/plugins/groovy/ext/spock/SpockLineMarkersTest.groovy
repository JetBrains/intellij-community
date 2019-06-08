// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.codeInsight.TestFrameworks
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

@CompileStatic
class SpockLineMarkersTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.addFileToProject 'spock/lang/Specification.groovy', '''\
package spock.lang
class Specification {}
'''
  }

  void 'test is test method'() {
    def file = (GroovyFile)fixture.configureByText('MySpec.groovy', '''\
class MySpec extends spock.lang.Specification {
  def cleanup() { expect: 1 == 1 }
  def methodWithoutLabels() {}
  def methodWithAnotherLabel() { expect2: 1 == 1 }
  <caret>def 'method with spaces ok'() { expect: 1 == 1 }
}
''')
    def data = [
      'cleanup'               : false,
      'methodWithoutLabels'   : false,
      'methodWithAnotherLabel': false,
      'method with spaces ok' : true,
    ]
    def spec = file.typeDefinitions.first()
    for (method in spec.codeMethods) {
      def name = method.name
      assert data[name] == TestFrameworks.instance.isTestMethod(method)
    }
//    assert fixture.findAllGutters().size() == 2
//    assert fixture.findGuttersAtCaret().size() == 1
  }
}
