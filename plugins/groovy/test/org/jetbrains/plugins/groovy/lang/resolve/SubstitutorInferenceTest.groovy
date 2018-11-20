// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Test

@CompileStatic
class SubstitutorInferenceTest extends GroovyLatestTest implements TypingTest {

  @Before
  void addClasses() {
    fixture.addFileToProject 'classes.groovy', '''\
interface I<T> {}
class C<T> implements I<T> {
  C(I<? extends T> c) {}
}
class PG {}
'''
  }

  @Test
  void 'raw in variable initializer'() {
    typingTest elementUnderCaret('List<String> l = <caret>new ArrayList()', GrNewExpression),
               'java.util.ArrayList'
  }

  @Test
  void 'explicit in variable initializer'() {
    typingTest elementUnderCaret('List<String> l = <caret>new ArrayList<Integer>()', GrNewExpression),
               'java.util.ArrayList<java.lang.Integer>'
  }

  @Test
  void 'diamond in variable initializer'() {
    typingTest(elementUnderCaret('I<PG> l = <caret>new C<>()', GrNewExpression), 'C<PG>')
  }

  @Test
  void 'diamond in tuple variable initializer'() {
    def expression = elementUnderCaret 'def (I<String> l) = [new<caret> C<>()]', GrNewExpression
    typingTest(expression, 'C<java.lang.String>')
  }

  @Test
  void 'diamond in tuple assignment initializer'() {
    def expression = elementUnderCaret 'I<String> l; (l) = [new<caret> C<>()]', GrNewExpression
    typingTest(expression, 'C<java.lang.String>')
  }

  @Test
  void 'diamond in argument of diamond in variable initializer'() {
    typingTest(elementUnderCaret('I<PG> l = new<caret> C<>(new C<>())', GrNewExpression), 'C<PG>')
  }

  @Test
  void 'diamond in argument of diamond in variable initializer 2'() {
    typingTest(elementUnderCaret('I<PG> l = new C<>(new<caret> C<>())', GrNewExpression), 'C<PG>')
  }

  @Test
  void 'call in argument of diamond in variable initializer'() {
    def call = elementUnderCaret('''\
def <T> T theMethod() {}
I<PG> l = new <caret> C<>(theMethod())
''', GrNewExpression)
    typingTest(call, 'C<PG>')
  }

  @Test
  void 'call in argument of diamond in variable initializer 2'() {
    def call = elementUnderCaret('''\
def <T> T theMethod() {}
I<PG> l = new C<>(theMethod<caret>())
''', GrMethodCall)
    typingTest(call, 'I<? extends PG>')
  }

  @Test
  void 'call in argument of call in variable initializer'() {
    def call = elementUnderCaret('''\
def <T> T theMethod() {}
def <T> T first(List<T> arg) {}
I<PG> l = <caret>first(theMethod())
''', GrMethodCall)
    typingTest(call, 'I<PG>')
  }

  @Test
  void 'call in argument of call in variable initializer 2'() {
    def call = elementUnderCaret('''\
def <T> T theMethod() {}
def <T> T first(List<T> arg) {}
I<PG> l = first(theMethod<caret>())
''', GrMethodCall)
    typingTest(call, 'java.util.List<I<PG>>')
  }

  @Test
  void 'type from argument'() {
    typingTest 'new ArrayList<>(new ArrayList<Integer>())', 'java.util.ArrayList<java.lang.Integer>'
  }
}
