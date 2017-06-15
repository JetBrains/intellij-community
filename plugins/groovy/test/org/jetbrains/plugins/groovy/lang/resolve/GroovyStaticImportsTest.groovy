/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

class GroovyStaticImportsTest extends LightGroovyTestCase {

  @CompileStatic
  protected GroovyFile configureByText(String text) {
    (GroovyFile)fixture.configureByText('_.groovy', text)
  }

  protected GroovyResolveResult[] multiResolveByText(String text) {
    def file = configureByText(text)
    def reference = file.findReferenceAt(fixture.editor.caretModel.offset)
    if (reference instanceof GroovyPolyVariantReference) {
      reference.multiResolve(false)
    }
    else {
      [(GroovyResolveResult)reference.resolve()]
    }
  }

  protected GroovyResolveResult advancedResolveByText(String text) {
    def results = multiResolveByText(text)
    assert results.size() == 1
    return results[0]
  }

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.addFileToProject 'foo/bar/Baz.groovy', '''\
package foo.bar;
class Baz {
  static def getSomeProp() { null }
  static void getSomeProp(a) {} // not really a getter  
  
  static void setSomeProp(a) {}
  static void setSomeProp() {}  // not really a setter
  
  static boolean isSomeProp() { true }
  static void isSomeProp(v) {}  // not really a boolean getter
  
  static Object someProp() { null }
  static Object someProp(p) { null }
  
  static class someProp {}  
}
'''
  }

  void 'test resolve rValue reference'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>someProp
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'getSomeProp'
    assert element.parameterList.parametersCount == 0
  }

  void 'test resolve lValue reference'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>someProp = 1
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'setSomeProp'
    assert element.parameterList.parametersCount == 1
  }

  void 'test resolve call to getter'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>getSomeProp()
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'getSomeProp'
    assert element.parameterList.parametersCount == 0
  }

  void 'test resolve call to not-a-getter'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>getSomeProp(2)
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'getSomeProp'
    assert element.parameterList.parametersCount == 1
  }

  void 'test resolve call to boolean getter'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>isSomeProp()
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'isSomeProp'
    assert element.parameterList.parametersCount == 0
  }

  void 'test resolve call to boolean not-a-getter'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>isSomeProp(3)
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'isSomeProp'
    assert element.parameterList.parametersCount == 1
  }

  void 'test resolve call to setter'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>setSomeProp(4)
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'setSomeProp'
    assert element.parameterList.parametersCount == 1
  }

  void 'test resolve call to not-a-setter'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>setSomeProp()
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'setSomeProp'
    assert element.parameterList.parametersCount == 0
  }

  void 'test resolve method call'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>someProp()
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'someProp'
    assert element.parameterList.parametersCount == 0
  }

  void 'test resolve another method call'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
<caret>someProp(5)
'''
    def element = result.element
    assert element instanceof PsiMethod
    assert element.name == 'someProp'
    assert element.parameterList.parametersCount == 1
  }

  void 'test resolve inner class'() {
    def result = advancedResolveByText '''\
import static foo.bar.Baz.someProp
new <caret>someProp()
'''
    def element = result.element
    assert element instanceof PsiClass
    assert element.name == 'someProp'
    assert element.containingClass.qualifiedName == 'foo.bar.Baz'
  }

  void 'test resolve to a field'() {
    fixture.addFileToProject 'foo/bar/ClassWithoutPropertyMethods.groovy', '''\
package foo.bar;

class ClassWithoutPropertyMethods {
  static Object someProp() { null }
  static Object someProp(p) { null }
  public static someProp
}
'''
    def result = advancedResolveByText '''\
import static foo.bar.ClassWithoutPropertyMethods.someProp
<caret>someProp
'''
    def element = result.element
    assert element instanceof PsiField
    assert element.name == 'someProp'
    assert element.containingClass.qualifiedName == 'foo.bar.ClassWithoutPropertyMethods'
  }
}
