// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter

class GroovyCompletion30Test extends GroovyCompletionTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  @Override
  protected void setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    super.setUp()
  }

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = true
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault()
    super.tearDown()
  }


  void testInferArgumentTypeFromClosure() {
    doBasicTest('''\
def foo(a, closure) {
  closure(a)
}

foo(1) { it.byt<caret> }
''', '''\
def foo(a, closure) {
  closure(a)
}

foo(1) { it.byteValue()<caret> }
''')
  }

  void testInferArgumentTypeFromLambda() {
    doBasicTest('''
def foo(a, closure) {
  closure(a)
}

foo(1, (it) -> it.byt<caret> )
''', '''
def foo(a, closure) {
  closure(a)
}

foo(1, (it) -> it.byteValue()<caret> )
''')
  }

  void testInferArgumentTypeFromClosureInsideClass() {
    doBasicTest '''
class K {
  def foo(a, closure) {
    closure(a)
  }

  def bar() {
    foo(1) { it.byt<caret> }
  }
}
''', '''
class K {
  def foo(a, closure) {
    closure(a)
  }

  def bar() {
    foo(1) { it.byteValue()<caret> }
  }
}
'''
  }

  void testInferArgumentTypeForClosure() {
    doBasicTest '''
def foo(a, b) { b(a) }

foo(1) { a -> a.byteValue() }
foo('q') { it.len<caret>}
''', '''
def foo(a, b) { b(a) }

foo(1) { a -> a.byteValue() }
foo('q') { it.length()<caret>}
'''
  }

  void testInferArgumentTypeFromMethod() {
    doBasicTest('''\
def foo(a) {
  a.byt<caret>
}

foo 1
''', '''\
def foo(a) {
  a.byteValue()<caret>
}

foo 1
''')
  }

  void testCompletionAfterComplexStatement() {
    doBasicTest '''
def x(List<Integer> l){}

void m(l) {
    x([l])
    l.by<caret>
}''', '''
def x(List<Integer> l){}

void m(l) {
    x([l])
    l.byteValue()<caret>
}'''
  }
}
