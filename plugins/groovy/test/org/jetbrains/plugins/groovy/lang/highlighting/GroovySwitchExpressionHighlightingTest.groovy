// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting


import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
class GroovySwitchExpressionHighlightingTest extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  void doTest(String text) {
    myFixture.configureByText("_.groovy", text)
    myFixture.doHighlighting()
    myFixture.checkResult(text)
  }

  void 'test no mixing arrows and colons'() {
    doTest '''
def x = switch (10) {
  case 20 <error>-></error> 10
  case 50<error>:</error>
    yield 5
}'''
  }

  void 'test require yield in colon-style switch expression'() {
    doTest '''
def x = switch (10) {
  <error>case</error> 20:
    40
}'''
  }

  void 'test forbid return in colon-style switch expression'() {
    doTest '''
def x = switch (10) {
  <error>case</error> 20:
    <error>return 40</error>
}'''
  }

  void 'test throw in colon-style switch expression'() {
    doTest '''
def x = switch (10) {
    case 20: 
        throw new IOException()
}'''
  }
}
