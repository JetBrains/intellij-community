package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

/**
 * @author peter
 */
class GppFunctionalTest extends LightCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyHighlightingTest.GROOVY_17_PROJECT_DESCRIPTOR;
  }

  protected void setUp() {
    super.setUp()
    myFixture.addClass("package groovy.lang; public @interface Typed {}")
  }

  public void testCastListToIterable() throws Exception {
    myFixture.addClass("class X extends java.util.ArrayList<Integer> {}")
    testAssignability """
X ints = [239, 4.2d]
X unassignable = <warning descr="Cannot assign 'List' to 'X'">['aaa']</warning>
"""
  }

  void testAssignability(String text) {
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    def file = myFixture.configureByText("a.groovy", """
@Typed def foo() {
  $text
}""")
    myFixture.testHighlighting(true, false, false, file.virtualFile)
  }

}
