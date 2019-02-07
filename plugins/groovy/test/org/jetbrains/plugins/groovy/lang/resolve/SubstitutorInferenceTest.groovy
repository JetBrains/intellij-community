// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiTypeParameterListOwner
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType

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

class GenericPropertyContainer {
  def <T> I<T> getGenericProperty() {}
  def <T> void setGenericProperty(I<T> c) {}
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
  void 'diamond in new expression'() {
    typingTest('new C<PG>(new<caret> C<>())', GrNewExpression, 'C<PG>')
  }

  @Test
  void 'diamond type from argument'() {
    expressionTypeTest('new C<>(new C<Integer>())', 'C<java.lang.Integer>')
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
  void 'explicit closure safe cast as argument of generic method'() {
    typingTest('''\
interface Producer<T> {}
static <T> T ppp(Producer<T> p) {}
<caret>ppp({} as Producer<String>)
''', GrMethodCall, 'java.lang.String')
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

  @Test
  void 'empty list literal from variable'() {
    typingTest('List<List<Integer>> l = [<caret>]', GrListOrMap, 'java.util.List<java.util.List<java.lang.Integer>>')
  }

  @Test
  void 'empty list literal from new expression'() {
    typingTest 'new ArrayList<Integer>([<caret>])', GrListOrMap, 'java.util.List<java.lang.Integer>'
  }

  @Test
  void 'empty list literal from diamond in new expression'() {
    typingTest 'new ArrayList<Integer>(new ArrayList<>([<caret>]))', GrListOrMap, 'java.util.List<java.lang.Integer>'
  }

  @Test
  void 'empty list literal from nested diamond in variable initializer'() {
    typingTest 'List<Integer> l = new ArrayList<>(new ArrayList<>([<caret>]))', GrListOrMap, 'java.util.List<java.lang.Integer>'
  }

  @Ignore("Requires list literal inference from both arguments and context type")
  @Test
  void 'empty list literal from outer list literal'() {
    typingTest('List<List<Integer>> l = [[<caret>]]', GrListOrMap, 'java.util.List<java.lang.Integer>')
  }

  /**
   * This test is wrong and exists only to preserve behaviour
   * and should fail when 'empty list literal from outer list literal' will pass.
   */
  @Test
  void 'list literal with empty list literal'() {
    typingTest('List<List<Integer>> l = <caret>[[]]', GrListOrMap, 'java.util.List<java.util.List<java.lang.Object>>')
    typingTest('List<List<Integer>> l = [[<caret>]]', GrListOrMap, 'java.util.List<java.lang.Object>')
  }

  @Ignore("Requires list literal inference from both arguments and context type")
  @Test
  void 'diamond from outer list literal'() {
    typingTest 'List<List<String>> l = [new <caret>ArrayList<>()]', GrNewExpression, 'java.util.ArrayList<java.lang.String>'
  }

  /**
   * This test is wrong and exists only to preserve behaviour
   * and should fail when 'diamond from outer list literal' will pass.
   */
  @Test
  void 'list literal with diamond'() {
    typingTest 'List<List<String>> l = [new <caret>ArrayList<>()]', GrNewExpression, 'java.util.ArrayList<java.lang.Object>'
    typingTest 'List<List<String>> l = <caret>[new ArrayList<>()]', GrListOrMap, 'java.util.List<java.util.ArrayList<java.lang.Object>>'
  }

  @Test
  void 'empty map literal in variable initializer'() {
    typingTest('Map<String, Integer> m = <caret>[:]', GrListOrMap, 'java.util.LinkedHashMap<java.lang.String, java.lang.Integer>')
  }

  @Test
  void 'generic getter from left type'() {
    def ref = elementUnderCaret('I<PG> lp = new GenericPropertyContainer().<caret>genericProperty', GrReferenceExpression)
    assertSubstitutor(ref.advancedResolve(), 'PG')
  }

  @Test
  void 'generic setter from argument'() {
    def ref = elementUnderCaret('new GenericPropertyContainer().<caret>genericProperty = new C<String>()', GrReferenceExpression)
    assertSubstitutor(ref.advancedResolve(), 'java.lang.String')
  }

  private static void assertSubstitutor(GroovyResolveResult result, String... expectedTypes) {
    def element = (PsiTypeParameterListOwner)result.element
    def typeParameters = element.typeParameters
    assert typeParameters.length == expectedTypes.length
    def substitutor = result.substitutor
    for (i in 0..<expectedTypes.length) {
      assertType(expectedTypes[i], substitutor.substitute(typeParameters[i]))
    }
  }
}
