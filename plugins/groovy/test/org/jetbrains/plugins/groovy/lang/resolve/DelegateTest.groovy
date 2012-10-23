/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMirrorElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class DelegateTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}resolve/delegate/";
  }
  
  private PsiMirrorElement doTest(String text) {
    def ref = configureByText(text)
    def resolved = ref.resolve()

    assertNotNull resolved
    assertInstanceOf resolved, PsiMirrorElement
    return resolved as PsiMirrorElement
  }
  
  
  void testSimple() {
    doTest('''
class A {
  def foo(){}
}

class B {
  @Delegate A a
}

new B().fo<caret>o()
''')
  }

  void testInheritance() {
    doTest('''
class Base {
  def foo(){}
}

class A extends Base {} 

class B {
  @Delegate A a
}

new B().fo<caret>o()
''')
  }
  
  void testSelectFirst1() {
    def resolved = doTest('''
class A1 {
  def foo(){}
}

class A2 {
  def foo(){}
}
  

class B {
  @Delegate A1 a1
  @Delegate A2 a2
}

new B().fo<caret>o()
''')

    def prototype = resolved.prototype as PsiMethod
    def cc = prototype.containingClass
    assertEquals 'A1', cc.name
  }

  void testSelectFirst2() {
    def resolved = doTest('''
class A1 {
  def foo(){}
}

class A2 {
  def foo(){}
}


class B {
  @Delegate A2 a2
  @Delegate A1 a1
}

new B().fo<caret>o()
''')

    def prototype = resolved.prototype as PsiMethod
    def cc = prototype.containingClass
    assertEquals 'A2', cc.name
  }
  
  void testSubstitutor1() {
    def resolved = doTest('''
class Base<T> {
  def foo(T t){}
}

class A<T> extends Base<List<T>> {
}


class B {
  @Delegate A<String> a2
}

new B().fo<caret>o([''])
''')

    assertInstanceOf(resolved, PsiMethod)
    def parameters = (resolved as PsiMethod).parameterList.parameters
    assertEquals 'java.util.List<java.lang.String>', parameters[0].type.canonicalText
  }

  void testCycle() {
    def file = myFixture.configureByText('a.groovy', '''
class A {
    def foo() {}
    
    @Delegate C c
}

class B {
    def test() {}
    
    @Delegate A a
}

class C {
    def bar(){}
    
    @Delegate B b
}



new A().foo()
new B().foo()
new C().foo()

new A().bar()
new B().bar()
new C().bar()

new A().test()
new B().test()
new C().test()
''') as GroovyFile

    file.statements.each {
      def method = (it as GrMethodCall).resolveMethod()
      assertNotNull method
    }
  }

  
  void testJavaDelegate() {
    myFixture.addFileToProject('A.java', '''
class Base {
  public void foo(){}
}
class A extends Base{}
''')

    doTest('''
class B {
  @Delegate A a
}

new B().fo<caret>o()''')
  }

  void testInheritanceCycle() {
    def file = myFixture.configureByText ('a.groovy', '''
class Base {
  @Delegate A a
}
class A extends Base {
  void foo(){}
}

new Base().foo()
new A().foo()''') as GroovyFile
    
    file.statements.each {
      assertNotNull((it as GrMethodCall).resolveMethod())
    }
  }
}
