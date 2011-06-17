package org.jetbrains.plugins.groovy.lang.overriding;


import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

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
    @Override
    def foo() {
        return super.foo()    //To change body of overridden methods use File | Settings | File Templates.
    }
}
"""
  }

  public void testMethodTypeParameters() {
    myFixture.addFileToProject "v.java", """
class Base<E> {
  public <T> T[] toArray(T[] t) {return (T[])new Object[0];}
}
"""
    myFixture.configureByText "a.groovy", """
class Test<T> extends Base<T> {<caret>}
"""
    generateImplementation(findMethod("Base", "toArray"))
    myFixture.checkResult """
class Test<T> extends Base<T> {
    @Override
    def <T> T[] toArray(T[] t) {
        return super.toArray(t)    //To change body of overridden methods use File | Settings | File Templates.
    }
}
"""
  }

  public void _testImplementIntention() {
    myFixture.configureByText('a.groovy', '''
class Base<E> {
  public <E> E fo<caret>o(E e){}
}

class Test extends Base<String> {
}
''')

    def fixes = myFixture.getAvailableIntentions()
    assertSize(1, fixes)

    def fix = fixes[0]
    fix.invoke(myFixture.project, myFixture.editor, myFixture.file)
  }

  private def generateImplementation(PsiMethod method) {
    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        GrTypeDefinition clazz = ((PsiClassOwner) myFixture.file).classes[0]
        OverrideImplementUtil.overrideOrImplement(clazz, method);
        PostprocessReformattingAspect.getInstance(myFixture.project).doPostponedFormatting()
      }
    });
    myFixture.editor.selectionModel.removeSelection()
  }

  def findMethod(String className, String methodName) {
    return JavaPsiFacade.getInstance(project).findClass(className).findMethodsByName(methodName, false)[0]
  }

}
