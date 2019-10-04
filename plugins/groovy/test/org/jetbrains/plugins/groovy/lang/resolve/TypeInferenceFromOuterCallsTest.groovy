// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.registry.Registry
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

@CompileStatic
class TypeInferenceFromOuterCallsTest extends TypeInferenceTestBase {

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

  void 'test outer call influence'() {
    doTest '''
def foo(a) {
  <caret>a
}

foo(1)
''', JAVA_LANG_INTEGER
  }

  void 'test implicit @ClosureParams'() {
    doTest '''
def foo(a, c) {
  c(a)
}

foo(1) { <caret>it }
''', JAVA_LANG_INTEGER
  }

  void 'test implicit @ClosureParams with nontrivial substitutor'() {
    doTest '''
class A<T> {
    T t = null
    def foo(c) {
        c(t)
    }
}

(new A<Integer>()).foo {
    <caret>it
}''', JAVA_LANG_INTEGER
  }

  void 'test implicit @ClosureParams with generified method'() {
    doTest '''
def foo(a, b) { b(a) }

foo(1) {}
foo('q') { <caret>it }
''', JAVA_LANG_STRING
  }

  void 'test implicit @ClosureParams with two parameters'() {
    doTest '''
def foo(cl) {
    cl(1, 'q')
}

foo { a, b ->
    <caret>a
}''', JAVA_LANG_INTEGER
  }

  void 'test DFA priority is higher than signature inference'() {
    doTest '''
def foo(a) {
  if (a instanceof Integer) {
    <caret>a
  }
}

foo(null as Number)
''', JAVA_LANG_INTEGER
  }
}
