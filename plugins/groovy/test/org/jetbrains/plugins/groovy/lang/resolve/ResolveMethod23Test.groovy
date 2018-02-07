// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor

class ResolveMethod23Test extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_2_3

  void 'test resolve upper bound type method'() {
    addCompileStatic()
    def method = resolveByText('''\
@groovy.transform.CompileStatic
def filter(Collection<? extends Number> numbers) {
    numbers.findAll { it.double<caret>Value() }
}
''', PsiMethod)
    assert method.containingClass.qualifiedName == 'java.lang.Number'
  }

  void 'test resolve unknown class reference'() {
    resolveByText('''\
def filter(a) {
    a.clas<caret>s
}
''', null)
  }

  void 'test resolve class reference on method'() {
    def method = resolveByText('''\
def b() {return new Object()}
def filter() {
    b().clas<caret>s
}
''', PsiMethod)
    assertEquals('getClass', method.name)
  }

  void 'test resolve unknown class reference CS'() {
    addCompileStatic()
    def method = resolveByText('''\
@groovy.transform.CompileStatic
def filter(a) {
    a.clas<caret>s
}
''', PsiMethod)
    assertEquals('getClass', method.name)
  }
}
