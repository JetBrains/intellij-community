/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.markup

import com.intellij.psi.impl.compiled.ClsMethodImpl
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl

/**
 * @author Sergey Evdokimov
 */
class XmlMarkupBuilderTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return ""
  }

  public void testHighlighting() {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection, GrUnresolvedAccessInspection)


    myFixture.configureByText("A.groovy", """
class A {
    void testSomething() {
        <warning>x</warning>()

        def xml = new groovy.xml.MarkupBuilder()
        xml.records() {
            car(name:'HSV Maloo', make:'Holden', year:2006) {
                x()
                country('Australia')
                record(type:'speed', 'Production Pickup Truck with speed of 271kph')
                map(a:'a', b:'b')
                emptyTag()
            }

            foo<warning>("a", "b")</warning>
        }
    }
}
""")

    myFixture.testHighlighting(true, false, true)
  }

  public void testResolveToMethod1() {
    myFixture.configureByText("A.groovy", """
class A {
    void foo() {}

    void testSomething() {
        def xml = new groovy.xml.MarkupBuilder()
        xml.records() {
            foo<caret>()
        }
    }
}
""")

    def method = myFixture.getElementAtCaret()

    assert method instanceof GrMethodImpl
    assert method.isPhysical()
    assert method.getContainingClass().getName() == "A"
  }

  public void testResolveToMethod2() {
    myFixture.configureByText("A.groovy", """
class A {
    void testSomething() {
        def xml = new groovy.xml.MarkupBuilder()
        xml.records() {
            getDoubleQuotes<caret>()
        }
    }
}
""")

    def method = myFixture.getElementAtCaret()

    assert method instanceof ClsMethodImpl
    assert method.isPhysical()
    assert method.containingClass.name == "MarkupBuilder"
  }
}
