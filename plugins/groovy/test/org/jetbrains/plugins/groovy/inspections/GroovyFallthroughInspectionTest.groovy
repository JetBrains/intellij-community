// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyFallthroughInspection

/**
 * @author Bas Leijdekkers
 */
class GroovyFallthroughInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK

  private void doTest(final String text) {
    myFixture.configureByText('_.groovy', text)

    myFixture.enableInspections(GroovyFallthroughInspection)
    myFixture.checkHighlighting(true, false, false)
  }

  void testBasic() {
    doTest('''
def x = 1.23
def result = ""

switch ( x ) {
    case "foo":
        result = "found foo"
        // lets fall through

    case "bar":
        result += "bar"

    <warning descr="Fallthrough in 'switch' statement">case</warning> [4, 5, 6, 'inList']:
        result = "list"
        break

    case 12..30:
        result = "range"
        break

    case Integer:
        result = "integer"
        break

    case Number:
        result = "number"
        break

    default:
        result = "default"
}

assert result == "number"
''')
  }

  void testNoInspectionWithArrow() {
    doTest """
def x = 1
switch(x) {
  case 10 -> 20
  case 20 -> 30
  default -> 40
}
"""
  }

  void testCheckYield() {
    doTest """
def x = 1
def y = switch(x) {
  case 1:
  <warning>case</warning> 10:
    yield 20
  case 20: 
    yield 30
  default: 
    yield 40
}
"""
  }

}
