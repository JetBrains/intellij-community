/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.codeInsight.TestFrameworks
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

@CompileStatic
class SpockLineMarkersTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
