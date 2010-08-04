package org.jetbrains.plugins.groovy.lang.overriding;


import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil

/**
 * @author peter
 */
public class GroovyOverrideImplementTest extends LightCodeInsightFixtureTestCase {

  public void testInEmptyBraces() throws Exception {
    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod(Object.name, "equals"))
    myFixture.checkResult """
class Test {
  @Override
  boolean equals(Object obj) {
    return super.equals(obj)    //To change body of overridden methods use File | Settings | File Templates.
  }
}
"""
  }
  
  public void testConstructor() throws Exception {
    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod(Object.name, "Object"))
    myFixture.checkResult """
class Test {
  def Test() {
    super()    //To change body of overridden methods use File | Settings | File Templates.
  }
}
"""
  }

  public void testNoSuperReturnType() throws Exception {
    myFixture.addFileToProject("Foo.groovy", """
    class Foo {
      def foo() {
        true
      }
    }""")

    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod("Foo", "foo"))
    myFixture.checkResult """
class Test {
  @Override def foo() {
    return super.foo()    //To change body of overridden methods use File | Settings | File Templates.
  }
}
"""
  }

  private def generateImplementation(PsiMethod method) {
    GrTypeDefinition clazz = ((PsiClassOwner) myFixture.file).classes[0]
    GroovyOverrideImplementUtil.generateImplementation myFixture.editor, myFixture.file, clazz, method, PsiSubstitutor.EMPTY
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
    myFixture.editor.selectionModel.removeSelection()
  }

  def findMethod(String className, String methodName) {
    return JavaPsiFacade.getInstance(project).findClass(className).findMethodsByName(methodName, false)[0]
  }

}
