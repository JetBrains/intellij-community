package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import com.intellij.psi.PsiFile

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

  public void testCastListToAnything() throws Exception {
    testAssignability """
File f1 = ['path']
File f2 = <warning descr="Cannot assign 'List' to 'File'">['path', 2, true, 42]</warning>
"""
  }

  public void testCastMapToAnotherMap() throws Exception {
    testAssignability """
HashMap<String, File> m1 = ['a':['b']]
HashMap<String, File> m2 = <warning descr="Cannot assign 'Map' to 'HashMap<String, File>'">['a':'b']</warning>
"""
  }

  public void testCastMapToObject() throws Exception {
    myFixture.addClass("class Foo { String name; void foo() {} }")
    testAssignability """
Foo f = [name: 'aaa', foo: { println 'hi' }, anotherProperty: 42 ]
"""
  }

  void testAssignability(String text) {
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    PsiFile file = configureTyped(text)
    myFixture.testHighlighting(true, false, false, file.virtualFile)
  }

  private PsiFile configureTyped(String text) {
    return myFixture.configureByText("a.groovy", """
@Typed def foo() {
  $text
}""")
  }

}
