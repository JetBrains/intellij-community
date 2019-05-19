// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.ResolveTest

class LambdaResolveTest extends GroovyResolveTestCase implements ResolveTest {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  void 'test resolve upper bound type method'() {
    def method = resolveByText('''\
@groovy.transform.CompileStatic
def filter(Collection<? extends Number> numbers) {
    numbers.findAll(it -> it.double<caret>Value())
}
''', PsiMethod)
    assert method.containingClass.qualifiedName == 'java.lang.Number'
  }

  void testIntersect() {
    resolveByText '''
class Base {
    void foo() {}
}
class D extends Base {}

Closure cl
boolean rand = Math.random() < 0.5
if (rand)
    cl = (D  p) -> p
else
    cl = (Base  p) -> p

cl(new D()).<caret>foo()
''', PsiMethod
  }

  void testInferPlusType() {
    resolveByText '''
[[1, 2, 3], [2, 3], [0, 2]].
  collect((it) -> {it + [56]}).
  findAll(it -> {it.si<caret>ze() >= 3})
''', PsiMethod
  }

  void testStringInjectionDontOverrideItParameter() {
    resolveByText '''
[2, 3, 4].collect (it) -> {"\${it.toBigDeci<caret>mal()}"}
''', PsiMethod
  }

  void testRuntimeMixin() {
    resolveByText '''\
class ReentrantLock {}

ReentrantLock.metaClass.withLock = (nestedCode) -> {}

new ReentrantLock().withLock(()-> {
    withL<caret>ock(2)
})
'''
  }

  void testMixin() {
    resolveByText '''
def foo() {
    Integer.metaClass.abc = () -> { print 'something' }
    1.a<caret>bc()
}
''', PsiMethod
  }

  void testCurry() {
    resolveByText '''
def c = (int i, j, k) -> i

c.curry(3, 4).call(5).<caret>intValue()
''', PsiMethod
  }
}
