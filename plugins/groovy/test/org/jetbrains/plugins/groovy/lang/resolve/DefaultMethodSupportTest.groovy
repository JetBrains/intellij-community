// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod

class DefaultMethodSupportTest extends GroovyResolveTestCase {
  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_3_0
  }

  void testSuperReferenceWithQualifier() {
    def method = resolveByText('''
interface A {
    default String exec() { 'A' }
}
interface B {
    default String exec() { 'B' }
}

class C implements A,B {
    String exec() {A.super.exe<caret>c() }
}
''', PsiMethod)
    assertTrue(method.containingClass.name == 'A')
  }

  void testSuperReferenceWithQualifier2() {
    def method = resolveByText('''
interface A {
    default String exec() { 'A' }
}
interface B {
    default String exec() { 'B' }
}

class C implements A, B {
    String exec() {B.super.exe<caret>c() }
}
''', PsiMethod)
    assertTrue(method.containingClass.name == 'B')
  }

  void testClashingMethods() {
    def method = resolveByText('''
interface A {
    default String exec() { 'A' }
}
interface B {
    default String exec() { 'B' }
}

class C implements A, B {
    String foo() {exe<caret>c() }
}
''', GrTraitMethod)
    assertEquals("B", method.prototype.containingClass.name)
  }

  void testDefaultMethodFromAsOperator1() {
    resolveByText('''
interface A {
  default foo(){}
}
class B {
  def bar() {}
}

def v = new B() as A
v.fo<caret>o()
''', PsiMethod)
  }

  void testDefaultMethodFromAsOperator2() {
    resolveByText('''
interface A {
  default foo(){}
}
class B {
  def bar() {}
}

def v = new B() as A
v.ba<caret>r()
''', PsiMethod)
  }

  void testDefaultMethodFromAssigning() {
    resolveByText '''
interface I {
    int foo() 
    default int bar() {
        2
    }
}

I i = {3}
i.ba<caret>r()
''', PsiMethod
  }

}
