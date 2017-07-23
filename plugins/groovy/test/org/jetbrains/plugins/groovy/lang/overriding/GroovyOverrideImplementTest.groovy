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
package org.jetbrains.plugins.groovy.lang.overriding

import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
/**
 * @author peter
 */
class GroovyOverrideImplementTest extends LightGroovyTestCase {

  void testInEmptyBraces() throws Exception {
    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod(Object.name, "equals"))
    myFixture.checkResult """
class Test {
    @Override
    boolean equals(Object obj) {
        return super.equals(obj)
    }
}
"""
  }

  void testConstructor() throws Exception {
    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod(Object.name, "Object"))
    myFixture.checkResult """
class Test {
    Test() {
        super()
    }
}
"""
  }

  void testNoSuperReturnType() throws Exception {
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
        return super.foo()
    }
}
"""
  }

  void testMethodTypeParameters() {
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
    def <T1> T1[] toArray(T1[] t) {
        return super.toArray(t)
    }
}
"""
  }

  void testThrowsList() {
    assertImplement('''\
class X implements I {
    <caret>
}

interface I {
    void foo() throws RuntimeException
}
''', 'I', 'foo', '''\
class X implements I {

    @Override
    void foo() throws RuntimeException {

    }
}

interface I {
    void foo() throws RuntimeException
}
''')
  }

  private void assertImplement(String textBefore, String clazz, String name, String textAfter) {
    myFixture.configureByText('a.groovy', textBefore)
    generateImplementation(findMethod(clazz, name))
    myFixture.checkResult(textAfter)
  }

  void testThrowsListWithImport() {
    myFixture.addClass('''\
package pack;
public class Exc extends RuntimeException {}
''')

    myFixture.addClass('''\
import pack.Exc;

interface I {
    void foo() throws Exc;
}
''')

    myFixture.configureByText('a.groovy', '''\
class X implements I {
    <caret>
}
''')

    generateImplementation(findMethod('I', 'foo'))

    myFixture.checkResult('''\
import pack.Exc

class X implements I {

    @Override
    void foo() throws Exc {

    }
}
''')
  }

  void testNullableParameter() {
    myFixture.addClass('''
package org.jetbrains.annotations;
public @interface Nullable{}
''')

    assertImplement('''
import org.jetbrains.annotations.Nullable

class Inheritor implements I {
  <caret>
}

interface I {
  def foo(@Nullable p)
}
''', 'I', 'foo', '''
import org.jetbrains.annotations.Nullable

class Inheritor implements I {

    @Override
    def foo(@Nullable Object p) {
        <caret>return null
    }
}

interface I {
  def foo(@Nullable p)
}
''')
  }

  void _testImplementIntention() {
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

  void 'test abstract final trait properties'() {
    myFixture.addFileToProject('T.groovy', '''\
trait T {
  abstract foo
  abstract final bar
}
''')
    myFixture.configureByText('classes.groovy', '''\
class <caret>A implements T {
}
''')
    myFixture.launchAction myFixture.findSingleIntention('Implement methods')
    myFixture.checkResult('''\
class A implements T {
    @Override
    Object getFoo() {
        return null
    }

    @Override
    void setFoo(Object foo) {

    }

    @Override
    Object getBar() {
        return null
    }
}
''')
  }

  private def generateImplementation(PsiMethod method) {
    WriteCommandAction.runWriteCommandAction project, {
      GrTypeDefinition clazz = (myFixture.file as PsiClassOwner).classes[0] as GrTypeDefinition
      OverrideImplementUtil.overrideOrImplement(clazz, method)
      PostprocessReformattingAspect.getInstance(myFixture.project).doPostponedFormatting()
    }
    myFixture.editor.selectionModel.removeSelection()
  }

  PsiMethod findMethod(String className, String methodName) {
    return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project)).findMethodsByName(methodName, false)[0]
  }

}
