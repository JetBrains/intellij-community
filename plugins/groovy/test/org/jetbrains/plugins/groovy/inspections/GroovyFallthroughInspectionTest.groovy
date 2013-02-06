package org.jetbrains.plugins.groovy.inspections

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyFallthroughInspection

/**
 * @author Bas Leijdekkers
 */
class GroovyFallthroughInspectionTest extends LightGroovyTestCase {
  final String basePath = null

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

    <warning descr="Fall-through in switch statement">case [4, 5, 6, 'inList']:</warning>
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
