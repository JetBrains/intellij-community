// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
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

class IdCallable {
  def <T> T call(T arg) { arg }
}
'''
  }

  @Test
  void 'raw in variable initializer'() {
    typingTest 'List<String> l = <caret>new ArrayList()', GrNewExpression, 'java.util.ArrayList'
  }

  @Test
  void 'explicit in variable initializer'() {
    typingTest('List<String> l = <caret>new ArrayList<Integer>()', GrNewExpression, 'java.util.ArrayList<java.lang.Integer>')
  }

  @Test
  void 'diamond in variable initializer'() {
    typingTest('I<PG> l = <caret>new C<>()', GrNewExpression, 'C<PG>')
  }

  @Test
  void 'diamond in tuple variable initializer'() {
    typingTest('def (I<String> l) = [new<caret> C<>()]', GrNewExpression, 'C<java.lang.String>')
  }

  @Test
  void 'diamond in tuple assignment initializer'() {
    typingTest('I<String> l; (l) = [new<caret> C<>()]', GrNewExpression, 'C<java.lang.String>')
  }

  @Test
  void 'diamond in argument of diamond in variable initializer'() {
    typingTest('I<PG> l = new<caret> C<>(new C<>())', GrNewExpression, 'C<PG>')
  }

  @Test
  void 'diamond in argument of diamond in variable initializer 2'() {
    typingTest('I<PG> l = new C<>(new<caret> C<>())', GrNewExpression, 'C<PG>')
  }

  @Test
  void 'call in argument of diamond in variable initializer'() {
    typingTest('''\
def <T> T theMethod() {}
I<PG> l = new <caret> C<>(theMethod())
''', GrNewExpression, 'C<PG>')
  }

  @Test
  void 'call in argument of diamond in variable initializer 2'() {
    typingTest('''\
def <T> T theMethod() {}
I<PG> l = new C<>(theMethod<caret>())
''', GrMethodCall, 'I<? extends PG>')
  }

  @Test
  void 'call in argument of call in variable initializer'() {
    typingTest('''\
def <T> T theMethod() {}
def <T> T first(List<T> arg) {}
I<PG> l = <caret>first(theMethod())
''', GrMethodCall, 'I<PG>')
  }

  @Test
  void 'call in argument of call in variable initializer 2'() {
    typingTest('''\
def <T> T theMethod() {}
def <T> T first(List<T> arg) {}
I<PG> l = first(theMethod<caret>())
''', GrMethodCall, 'java.util.List<I<PG>>')
  }

  @Test
  void 'diamond type from argument'() {
    expressionTypeTest('new ArrayList<>(new ArrayList<Integer>())', 'java.util.ArrayList<java.lang.Integer>')
  }

  @Test
  void 'closure safe cast as argument of method'() {
    typingTest('''\
interface F<T,U> { U foo(T arg); }              // T -> U
interface G<V,X> extends F<List<V>, List<X>> {} // List<V> -> List<X>
void foo(F<List<String>, List<Integer>> f) {}
foo({} <caret>as G)
''', GrSafeCastExpression, 'G<java.lang.String,java.lang.Integer>')
  }

  @Test
  void 'closure safe cast as argument of diamond constructor'() {
    typingTest('''\
interface F<T,U> { U foo(T arg); }
abstract class Wrapper<V, X> implements F<V, X> {
  Wrapper(F<V, X> wrappee) {}
}
F<Integer, String> w = new Wrapper<>({} <caret>as F)
    ''', GrSafeCastExpression, 'F<java.lang.Integer,java.lang.String>')
  }

  @Test
  void 'implicit call in variable initializer'() {
    typingTest('String s = <caret>new IdCallable()()', GrMethodCall, 'java.lang.String')
  }

  @Test
  void 'implicit call in argument of diamond in variable initializer'() {
    typingTest('C<Integer> s = new C<>(<caret>new IdCallable()())', GrMethodCall, 'I<? extends java.lang.Integer>')
  }

  @Test
  void 'implicit call from argument'() {
    expressionTypeTest('new IdCallable()("hi")', 'java.lang.String')
  }

  @Test
  void 'vararg method call type from argument'() {
    expressionTypeTest('static <T> List<T> foo(T... t) {}; foo("")', 'java.util.List<java.lang.String>')
    expressionTypeTest('static <T> List<T> foo(T... t) {}; foo(1d, 2l)', 'java.util.List<java.lang.Number>')
  }

  @Test
  void 'vararg method call type from array argument'() {
    expressionTypeTest('static <T> List<T> foo(T... t) {}; foo("".split(""))', 'java.util.List<java.lang.String>')
  }
}
