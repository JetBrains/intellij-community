// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod

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
  
  static private someProp       // field
  static class someProp {}
  
  static groovyProp             // property with private field and a getter/setter
  static final finalGroovyProp  // property with private field and a getter
  static methodWithDefaultParams(a,b,c=1) {}  
}
'''
  }

  void 'test static import reference'() {
    def results
    def element

    results = multiResolveByText 'import static foo.bar.Baz.<caret>someProp'
    assert results.size() == 10 // field is not valid result, but groovy ignores it

    results = multiResolveByText 'import static foo.bar.Baz.<caret>getSomeProp'
    assert results.size() == 2 // both getter and getter-like method are included

    results = multiResolveByText 'import static foo.bar.Baz.<caret>groovyProp'
    assert results.size() == 1 // getter and setter collapsed into property
    element = results[0].element
    assert element instanceof GrField

    results = multiResolveByText 'import static foo.bar.Baz.<caret>getGroovyProp'
    assert results.size() == 1 // getter only
    element = results[0].element
    assert element instanceof GrAccessorMethod

    results = multiResolveByText 'import static foo.bar.Baz.<caret>finalGroovyProp'
    assert results.size() == 1 // getter collapsed into property
    element = results[0].element
    assert element instanceof GrField

    results = multiResolveByText 'import static foo.bar.Baz.<caret>getFinalGroovyProp'
    assert results.size() == 1 // getter only
    element = results[0].element
    assert element instanceof GrAccessorMethod

    results = multiResolveByText 'import static foo.bar.Baz.<caret>methodWithDefaultParams'
    assert results.size() == 1 // collapsed into base
    element = results[0].element
    assert element instanceof GrMethod && !(element instanceof GrReflectedMethod)
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

  void 'test resolve property via static getter import with caches'() {
    fixture.enableInspections GrUnresolvedAccessInspection
    configureByText "import static foo.bar.Baz.getSomeProp; someProp"
    fixture.checkHighlighting()
  }
}
