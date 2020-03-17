// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT

@CompileStatic
class ExpressionTypeTest extends GroovyLatestTest implements TypingTest {

  @Test
  void 'untyped local variable reference @CS'() {
    typingTest '@groovy.transform.CompileStatic def usage() { def a; <caret>a }', JAVA_LANG_OBJECT
  }

  @Test
  void 'untyped parameter reference @CS'() {
    typingTest '@groovy.transform.CompileStatic def usage(a) { <caret>a }', JAVA_LANG_OBJECT
  }

  @Test
  void 'ternary with primitive types'() {
    expressionTypeTest "char a = '1'; char b = '1'; c ? a : b", JAVA_LANG_CHARACTER
  }

  @Test
  void 'unary increment'() {
    fixture.addFileToProject 'classes.groovy', '''\
class A { B next() { new B() } }
class B {}
'''
    expressionTypeTest 'new A()++', 'A'
    expressionTypeTest '++new A()', 'B'
  }

  @Test
  void 'this inside anonymous definition'() {
    typingTest '''
class Test {
    void main2() {
        new A(th<caret>is){}
    }
}

class A{}
''', "Test"
  }

  @Test
  void 'super inside anonymous definition'() {
    typingTest '''
class Test extends C {
    void main2() {
        new A(su<caret>per.equals("")){}
    }
}
class C {}
class B {}
class A extends B {}
''', "C"
  }
}
