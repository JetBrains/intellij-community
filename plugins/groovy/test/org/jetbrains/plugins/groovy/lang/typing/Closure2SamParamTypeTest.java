// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.LightProjectTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

@CompileStatic
class Closure2SamParamTypeTest extends LightProjectTest implements TypingTest {

  @Override
  LightProjectDescriptor getProjectDescriptor() {
    GroovyProjectDescriptors.GROOVY_LATEST
  }

  @Before
  void addClasses() {
    fixture.addFileToProject 'classes.groovy', '''\
interface I { def foo(String s) }
class C { C(int x, I i, String s) {} }
'''
  }

  @Test
  void 'argument of method call'() {
    typingTest 'def bar(I i) {}; bar { <caret>it }', JAVA_LANG_STRING
  }

  @Test
  void 'argument of literal constructor'() {
    typingTest 'C c = [42, { <caret>it }, "hi"]', JAVA_LANG_STRING
  }

  @Test
  void 'implicit return statement'() {
    typingTest '''I bar() {
  { it -> <caret>it }
}''', JAVA_LANG_STRING
  }

  @Test
  void 'explicit return statement'() {
    typingTest '''I bar() {
  return { it -> <caret>it }
}''', JAVA_LANG_STRING
  }
}
