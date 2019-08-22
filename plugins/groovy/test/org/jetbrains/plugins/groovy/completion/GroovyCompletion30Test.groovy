// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyCompletion30Test extends GroovyCompletionTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = true
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
}
