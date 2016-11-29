/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

    <warning descr="Fallthrough in switch statement">case [4, 5, 6, 'inList']:</warning>
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
