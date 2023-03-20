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

package org.jetbrains.plugins.groovy.lang.resolve


import com.intellij.psi.JavaResolveResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaReference
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.light.LightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod
import org.jetbrains.plugins.groovy.util.TestUtils

class JavaToGroovyResolveTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}resolve/javaToGroovy/"
  }

  void testField1() throws Exception {
    PsiReference ref = configureByFile("field1/A.java")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrField)
  }

  void testAccessorRefToProperty() throws Exception {
    PsiReference ref = configureByFile("accessorRefToProperty/A.java")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrAccessorMethod)
  }

  void testMethod1() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("method1/A.java")
    JavaResolveResult resolveResult = ref.advancedResolve(false)
    assertTrue(resolveResult.element instanceof GrMethod)
    assertTrue(resolveResult.validResult)
  }

  void testScriptMain() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("scriptMain/A.java")
    JavaResolveResult resolveResult = ref.advancedResolve(false)
    assertInstanceOf(resolveResult.element, LightMethodBuilder.class)
    assertTrue(resolveResult.validResult)
  }

  void testScriptMethod() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("scriptMethod/A.java")
    JavaResolveResult resolveResult = ref.advancedResolve(false)
    assertTrue(resolveResult.element instanceof GrMethod)
    assertTrue(resolveResult.validResult)
  }

  void testNoDGM() throws Exception {
    PsiJavaReference ref = (PsiJavaReference) configureByFile("noDGM/A.java")
    assertNull(ref.advancedResolve(false).element)
  }
  
  void testPrivateTopLevelClass() {
    myFixture.addFileToProject('Foo.groovy', 'private class Foo{}')

    def ref = configureByText('A.java', '''
class A {
  void foo() {
    Object o = new Fo<caret>o();
  }
}''')
    assertNotNull(ref.resolve())
  }

  void testScriptMethods() {
    myFixture.addFileToProject('Foo.groovy', '''\
void foo(int x = 5) {}
void foo(String s) {}
''')

    final ref = configureByText('A.java', '''\
class A {
  void bar() {
    new Foo().fo<caret>o()
  }
}''')

    assertInstanceOf(ref.resolve(), GrReflectedMethod)
  }

}