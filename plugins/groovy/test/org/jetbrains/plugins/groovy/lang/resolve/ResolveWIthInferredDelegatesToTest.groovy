// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter


@CompileStatic
class ResolveWIthInferredDelegatesToTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  @Override
  void setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    super.setUp()
  }

  @Override
  void tearDown() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault()
    super.tearDown()
  }

  void 'test inferred delegation'() {
    assertScript '''\
def foo(closure) {
  closure.delegate = 1
}

foo {
  byt<caret>eValue()
}
''', 'java.lang.Integer'
  }

  void 'test inferred delegation from dgm'() {
    assertScript '''\
def foo(closure) {
  1.with closure
}

foo {
  byt<caret>eValue()
}
''', 'java.lang.Integer'
  }

  void 'test inferred delegation from type parameter'() {
    assertScript '''\
class A<T> {
  
  def foo(closure) {
  (null as T).with closure
  }
}

(new A<Integer>()).foo {
  byt<caret>eValue()
}
''', 'java.lang.Integer'
  }


  private void assertScript(String text, String resolvedClass) {
    def resolved = resolveByText(text, PsiMethod)
    final containingClass = resolved.containingClass.qualifiedName
    assertEquals(resolvedClass, containingClass)
  }
}
