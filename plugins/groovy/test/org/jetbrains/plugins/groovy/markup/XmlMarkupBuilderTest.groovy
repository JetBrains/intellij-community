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


    myFixture.addFileToProject("A.groovy", """
class A {
    void testSomething() {
        <warning>x</warning>()

        def xml = new groovy.xml.MarkupBuilder()
        xml.records() {
            car(name:'HSV Maloo', make:'Holden', year:2006) {
                x()
                country('Australia')
                record(type:'speed', 'Production Pickup Truck with speed of 271kph')
            }

            foo<warning>("a", "b")</warning>
        }
    }
}
""")

    myFixture.testHighlighting(true, false, true, "A.groovy")
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
