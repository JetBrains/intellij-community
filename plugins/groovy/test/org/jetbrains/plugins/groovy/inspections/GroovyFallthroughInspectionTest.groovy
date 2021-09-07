// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.inspections

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyFallthroughInspection

/**
 * @author Bas Leijdekkers
 */
class GroovyFallthroughInspectionTest extends LightGroovyTestCase {

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

}
