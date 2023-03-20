// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiTypeParameterListOwner
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.LightProjectTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static com.intellij.psi.CommonClassNames.*
import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType

@CompileStatic
class SubstitutorInferenceTest extends LightProjectTest implements TypingTest, ResolveTest {

  @Override
  LightProjectDescriptor getProjectDescriptor() {
    GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK
  }

  @Before
  void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
  }

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
  
  def <T> List<T> getGenericList() {}
  def <T> void setGenericList(List<T> l) {}
}

class Files {
  List<File> getFiles() {}
  void setFiles(List<File> a) {}
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
    def expression = elementUnderCaret('I<PG> l = <caret>new C<>()', GrNewExpression)
    assertType('C<PG>', expression.type)
    def resolved = (MethodResolveResult)expression.advancedResolve()
    def typeParameter = resolved.element.containingClass.typeParameters.first()
    assertType('PG', resolved.substitutor.substitute(typeParameter))
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
  void 'non-closure safe cast'() {
    def expression = elementUnderCaret '"hi" <caret>as Number', GrSafeCastExpression
    assertSubstitutor(expression.reference.advancedResolve(), JAVA_LANG_NUMBER)
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
    typingTest 'List<List<String>> l = <caret>[new ArrayList<>()]', GrListOrMap, 'java.util.ArrayList<java.util.ArrayList<java.lang.Object>>'
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

  @Test
  void 'plus assignment'() {
    def op = elementUnderCaret('new Files().files <caret>+= new File(".")', GrAssignmentExpression)
    assertSubstitutor(op.reference.advancedResolve(), 'java.io.File')
  }

  @Test
  void 'plus assignment generic property r-value'() {
    //RecursionManager.disableAssertOnRecursionPrevention(fixture.testRootDisposable)
    //RecursionManager.disableMissedCacheAssertions(fixture.testRootDisposable)
    def ref = elementUnderCaret('new GenericPropertyContainer().<caret>genericList += new ArrayList<String>()', GrReferenceExpression)
    assertSubstitutor(ref.RValueReference.advancedResolve(), JAVA_LANG_STRING)
  }

  @Test
  void 'plus assignment generic property'() {
    RecursionManager.disableAssertOnRecursionPrevention(fixture.testRootDisposable)
    RecursionManager.disableMissedCacheAssertions(fixture.testRootDisposable)
    def op = elementUnderCaret('new GenericPropertyContainer().genericList <caret>+= new ArrayList<String>()', GrAssignmentExpression)
    assertSubstitutor(op.reference.advancedResolve(), JAVA_LANG_STRING)
  }

  @Ignore("we don't yet infer l-value substitutors")
  @Test
  void 'plus assignment generic property l-value'() {
    def ref = elementUnderCaret('new GenericPropertyContainer().<caret>genericList += new ArrayList<String>()', GrReferenceExpression)
    assertSubstitutor(ref.LValueReference.advancedResolve(), JAVA_LANG_STRING)
  }

  @Test
  void 'plus assignment with index r-value'() {
    def op = elementUnderCaret('Map<Number, String> mns; mns<caret>[42] += "foo"', GrIndexProperty)
    assertSubstitutor(op.RValueReference.advancedResolve(), JAVA_LANG_NUMBER, JAVA_LANG_STRING)
  }

  @Test
  void 'plus assignment with index l-value'() {
    def op = elementUnderCaret('Map<Number, String> mns; mns<caret>[42] += "foo"', GrIndexProperty)
    assertSubstitutor(op.LValueReference.advancedResolve(), JAVA_LANG_NUMBER, JAVA_LANG_STRING)
  }

  @Test
  void 'same method nested'() {
    def call = elementUnderCaret '''\
static <T> T run(Closure<T> c) {}
<caret>run(run { return { 42 } })
''', GrMethodCall
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_INTEGER)
  }

  @Test
  void 'chained with'() {
    resolveTest '''\
class A { def aMethod() { "42" } }
"bar".with { new A() }.with { it.<caret>aMethod() }
''', GrMethod
  }

  @Test
  void 'Collectors toList'() {
    def call = elementUnderCaret '''\
static void testCode(java.util.stream.Stream<Integer> ss) {
  ss.collect(java.util.stream.Collectors.<caret>toList())
}
''', GrMethodCall
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_INTEGER)
  }

  @Test
  void 'static call with raw argument with left type'() {
    def call = elementUnderCaret 'static <T> T lll(List<T> l) {}; List l; Date d = <caret>lll(l)', GrMethodCall
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_OBJECT)
  }

  @Test
  void 'dgm call on raw receiver with left type'() {
    def call = elementUnderCaret 'List l; Date d = l.<caret>getAt(0)', GrMethodCall
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_OBJECT)
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
