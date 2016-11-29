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
package org.jetbrains.plugins.groovy.builder

import com.intellij.psi.impl.compiled.ClsMethodImpl
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder

/**
 * @author Sergey Evdokimov
 */
class XmlMarkupBuilderTest extends LightGroovyTestCase {

  void testHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection, GrUnresolvedAccessInspection)
    myFixture.configureByText "A.groovy", """\
new groovy.xml.MarkupBuilder().root {
    a()
    b {}
    c(2) {}
    d(a: 1) {}
    e(b: 2, {})

    f(c: 3, d: 4)
    g(e: 5, [1, 2, 3], f: 6)
    h([g: 7, h: 8], new Object())
    i(new Object(), i: 9, j: 10) {}
    j([k: 11, l: 12], new Object(), {})

    k(new Object())
    l(new Object(), [m: 13])
    m(new Object(), [n: 14]) {}
    n(new Object(), [o: 15], {})

    foo<warning>("a", "b")</warning>
}
"""
    myFixture.testHighlighting(true, false, true)
  }

  void testResolveToMethod1() {
    myFixture.configureByText "A.groovy", """
class A {
    void foo() {}

    void testSomething() {
        def xml = new groovy.xml.MarkupBuilder()
        xml.records() {
            foo<caret>()
        }
    }
}
"""

    def method = myFixture.getElementAtCaret()
    assert method instanceof GrMethodImpl
    assert method.isPhysical()
    assert method.getContainingClass().getName() == "A"
  }

  void testResolveToMethod2() {
    myFixture.configureByText "A.groovy", """\
class A {
    void testSomething() {
        def xml = new groovy.xml.MarkupBuilder()
        xml.records() {
            getDoubleQuotes<caret>()
        }
    }
}
"""

    def method = myFixture.getElementAtCaret()
    assert method instanceof ClsMethodImpl
    assert method.isPhysical()
    assert method.containingClass.name == "MarkupBuilder"
  }

  void testResolveToDynamicMethod() {
    myFixture.configureByText "A.groovy", """\
def xml = new groovy.xml.MarkupBuilder()
xml.records() {
  foo<caret>()
}
"""

    def method = myFixture.getElementAtCaret()
    assert method instanceof GrLightMethodBuilder
    assert method.returnType.canonicalText == 'java.lang.String'
  }
}
