// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyResultOfIncrementOrDecrementUsedInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrAssignabilityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new GroovyAssignabilityCheckInspection[]{new GroovyAssignabilityCheckInspection()});

  @Test
  public void testIncompatibleTypesAssignments() { highlighting.get().doTest(); }

  @Test
  public void testDefaultMapConstructorNamedArgs() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    LightGroovyFixtures.addBigDecimal(myFixture);
    highlighting.get().doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  @Test
  public void testDefaultMapConstructorNamedArgsError() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    LightGroovyFixtures.addBigDecimal(myFixture);
    highlighting.get().doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  @Test
  public void testDefaultMapConstructorWhenDefConstructorExists() {
    highlighting.get().doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  @Test
  public void testUnresolvedMethodCallWithTwoDeclarations() {
    highlighting.get().doTest();
  }

  @Test
  public void testConstructor() {
    highlighting.get().doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  @Test
  public void testEverythingAssignableToString() { highlighting.get().doTest(); }

  @Test
  public void testMethodCallWithDefaultParameters() { highlighting.get().doTest(); }

  @Test
  public void testClosureWithDefaultParameters() { highlighting.get().doTest(); }

  @Test
  public void testMethodWithDefaultParametersAndVarargs() {
    highlighting.get().doTestHighlighting("""
                         def go(String a, String b = 'b', String c, int ... i) {}
                         go('a', 'c', 1, 2, 3)
                         """);
  }

  @Test
  public void testClosureApplicability() { highlighting.get().doTest(); }

  @Test
  public void testSingleParameterMethodApplicability() { highlighting.get().doTest(); }

  @Test
  public void testCallIsNotApplicable() { highlighting.get().doTest(); }

  @Test
  public void testPathCallIsNotApplicable() { highlighting.get().doTest(); }

  @Test
  public void testByteArrayArgument() { highlighting.get().doTest(); }

  @Test
  public void testPutValueToEmptyMap() { highlighting.get().doTest(); }

  public void _testPutIncorrectValueToMap() { highlighting.get().doTest(); }

  @Test
  public void testTupleTypeAssignments() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    LightGroovyFixtures.addBigDecimal(myFixture);
    highlighting.get().doTest();
  }

  @Test
  public void testSignatureIsNotApplicableToList() {
    highlighting.get().doTest();
  }

  @Test
  public void testInheritConstructorsAnnotation() {
    highlighting.get().doTest();
  }

  @Test
  public void testCollectionAssignments() { highlighting.get().doTest(); }

  @Test
  public void testReturnAssignability() { highlighting.get().doTest(); }

  @Test
  public void testMapNotAcceptedAsStringParameter() { highlighting.get().doTest(); }

  @Test
  public void testRawTypeInAssignment() { highlighting.get().doTest(); }

  @Test
  public void testMapParamWithNoArgs() { highlighting.get().doTest(); }

  @Test
  public void testInheritInterfaceInDelegate() {
    highlighting.get().doTest();
  }

  @Test
  public void testThisTypeInStaticContext() {
    highlighting.get().doTest();
  }

  @Test
  public void testAnonymousClassArgList() {
    highlighting.get().doTest();
  }

  @Test
  public void testTupleConstructorAttributes() {
    highlighting.get().doTest();
  }

  @Test
  public void testCanonicalConstructorApplicability() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("package groovy.transform; public @interface Canonical {}");
    highlighting.get().doTest();
  }

  @Test
  public void testStringAssignableToChar() {
    highlighting.get().doTest();
  }

  @Test
  public void testCurrying() {
    highlighting.get().doTest();
  }

  @Test
  public void testAnotherCurrying() {
    highlighting.get().doTest();
  }

  @Test
  public void testResultOfIncUsed() {
    highlighting.get().doTest(new GroovyResultOfIncrementOrDecrementUsedInspection());
  }

  @Test
  public void testNativeMapAssignability() {
    highlighting.get().doTest();
  }

  @Test
  public void testTwoLevelGrMap() {
    highlighting.get().doTest();
  }

  @Test
  public void testPassingCollectionSubtractionIntoGenericMethod() {
    highlighting.get().doTest(new GrUnresolvedAccessInspection());
  }

  @Test
  public void testImplicitEnumCoercion() {
    highlighting.get().doTest();
  }

  @Test
  public void testUnknownVarInArgList() {
    highlighting.get().doTest();
  }

  @Test
  public void testCallableProperty() {
    highlighting.get().doTest(new GrUnresolvedAccessInspection());
  }

  @Test
  public void testEnumConstantConstructors21() {
    highlighting.get().doTest();
  }

  @Test
  public void testSpreadArguments() {
    highlighting.get().doTest();
  }

  @Test
  public void testDiamondTypeInferenceSOE() {
    highlighting.get().doTestHighlighting("""
                         Map<Integer, String> a; a[2] = [:]""", false, false, false);
  }

  public void _testThisInStaticMethodOfAnonymousClass() {
    highlighting.get().doTestHighlighting("""
                         class A {
                             static abc
                             def foo() {
                                 new Runnable() {
                                     <error descr="Inner classes cannot have static declarations">static</error> void run() {
                                         print abc
                                     }
                                 }.run()
                             }
                         }""", true, false, false);
  }

  @Test
  public void testNonInferrableArgsOfDefParams() {
    boolean registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE);
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    try {
      highlighting.get().doTestHighlighting("""
                           def foo0(def a) { }
                           def bar0(def b) { foo0(b) }

                           def foo1(Object a) { }
                           def bar1(def b) { foo1(b) }

                           def foo2(String a) { }
                           def bar2(def b) { foo2(b) }
                           """);
    }
    finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue);
    }
  }

  @Test
  public void testPutAtApplicability() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("""
                          package java.util;
                          public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}
                          """);

    highlighting.get().doTestHighlighting("""
                         LinkedHashMap<File, List<File>> files = [:]
                         files[new File('a')] = [new File('b')]
                         files<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.io.File, java.io.File)'">[new File('a')]</warning> = new File('b')
                         """);
  }

  @Test
  public void testStringToCharAssignability() {
    highlighting.get().doTestHighlighting("""
                         def foo(char c){}

                         foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('a')</warning>
                         foo('a' as char)
                         foo('a' as Character)

                         char c = 'a'
                         """);
  }

  @Test
  public void testMethodRefs1() {
    highlighting.get().doTestHighlighting("""
                         class A {
                           int foo(){2}

                           Date foo(int x) {null}
                         }

                         def foo = new A().&foo

                         int i = foo()
                         int <warning descr="Cannot assign 'Date' to 'int'">i2</warning> = foo(2)
                         Date d = foo(2)
                         Date <warning descr="Cannot assign 'int' to 'Date'">d2</warning> = foo()
                         """);
  }

  @Test
  public void testMethodRefs2() {
    highlighting.get().doTestHighlighting("""
                         class Bar {
                           def foo(int i, String s2) {s2}
                           def foo(int i, int i2) {i2}
                         }

                         def cl = new Bar<error descr="'(' expected, got '.&'">.</error>&foo
                         cl = cl.curry(1)
                         String s = cl("2")
                         int <warning descr="Cannot assign 'String' to 'int'">s2</warning> = cl("2")
                         int i = cl(3)
                         String i2 = cl(3)
                         """);
  }

  @Test
  public void testThrowObject() {
    highlighting.get().doTestHighlighting("""
                         def foo() {
                           throw new RuntimeException()
                         }
                         def bar () {
                           <warning descr="Cannot assign 'Object' to 'Throwable'">throw</warning> new Object()
                         }

                         def test() {
                           throw new Throwable()
                         }
                         """);
  }

  @Test
  public void testCategoryWithPrimitiveType() {
    highlighting.get().doTestHighlighting("""
                         class Cat {
                           static foo(Integer x) {}
                         }

                         use(Cat) {
                           1.with {
                             foo()
                           }

                           (1 as int).foo()
                         }

                         class Ca {
                           static foo(int x) {}
                         }

                         use(Ca) {
                           1.<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
                           (1 as int).<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
                         }
                         """, GrUnresolvedAccessInspection.class);
  }

  @Test
  public void testCompileStaticWithAssignabilityCheck() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    myFixture.addClass("""
                          package groovy.transform;
                          public @interface CompileStatic {
                          }""");

    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         class A {

                           def foo(String s) {
                             int <warning descr="Cannot assign 'Date' to 'int'">x</warning> = new Date()
                           }

                           @CompileStatic
                           def bar() {
                             int <error descr="Cannot assign 'Date' to 'int'">x</error> = new Date()
                           }
                         }
                         """);
  }

  @Test
  public void testClosuresInAnnotations() {
    highlighting.get().doTestHighlighting("""
                         @interface Test {
                           Class value()
                         }

                         @interface Other {
                           String value()
                         }

                         @Test({String}) def foo1(){}
                         @Test({2.class}) def foo2(){}
                         @Test({2}) def foo3(){}
                         @Test({abc}) def foo4(){}
                         @Test(String) def foo5(){}
                         """);
  }

  @Test
  public void testTupleAssignment() {
    highlighting.get().doTestHighlighting("""
                         String x
                         int y
                         (x, <warning descr="Cannot assign 'String' to 'int'">y</warning>) = foo()

                         print x + y

                         List<String> foo() {[]}
                         """);
  }

  @Test
  public void testTupleDeclaration() {
    highlighting.get().doTestHighlighting("""
                         def (int <warning descr="Cannot assign 'String' to 'int'">x</warning>, String y) = foo()

                         List<String> foo() {[]}
                         """);
  }

  @Test
  public void testCastClosureToInterface() {
    highlighting.get().doTestHighlighting("""
                         interface Function<D, F> {
                             F fun(D d)
                         }

                         def foo(Function<String, String> function) {
                          //   print function.fun('abc')
                         }


                         foo<warning descr="'foo' in '_' cannot be applied to '(Function<java.lang.Double,java.lang.Double>)'">({println  it.byteValue()} as Function<Double, Double>)</warning>
                         foo({println  it.substring(1)} as Function)
                         foo({println  it.substring(1)} as Function<String, String>)
                         foo<warning descr="'foo' in '_' cannot be applied to '(groovy.lang.Closure)'">({println it})</warning>

                         """);
  }

  @Test
  public void testVarargsWithoutTypeName() {
    highlighting.get().doTestHighlighting("""
                         def foo(String key, ... params) {

                         }

                         foo('anc')
                         foo('abc', 1, '')
                         foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.Integer)'">(5)</warning>
                         """);
  }

  @Test
  public void testIncorrectReturnValue() {
    highlighting.get().doTestHighlighting("""
                         private int getObjects() {
                             try {
                                 def t = "test";
                                 t.substring(0);
                             }
                             finally {
                                 //...
                             }

                             <warning descr="Cannot return 'String' from method returning 'int'">return</warning> '';;
                         }
                         """);
  }

  @Test
  public void testForInAssignability() {
    highlighting.get().doTestHighlighting("""
                         for (<warning descr="Cannot assign 'String' to 'int'">int x</warning> in ['a']){}
                         """);
  }

  @Test
  public void testAssignabilityOfMethodProvidedByCategoryAnnotation() {
    highlighting.get().doTestHighlighting("""
                         @Category(List)
                         class EvenSieve {
                             def getNo2() {
                                 removeAll { 4 % 2 == 0 } //correct access
                                 add<warning descr="'add' in 'java.util.List<E>' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>
                             }
                         }
                         """);
  }

  @Test
  public void testAssignabilityOfCategoryMethod() {
    highlighting.get().doTestHighlighting("""

                         class Cat {
                           static foo(Class c, int x) {}
                         }

                         class X {}

                         use(Cat) {
                           X.foo(1)
                         }

                         """);
  }

  @Test
  public void testImplicitConversionToArray() {
    highlighting.get().doTestHighlighting("""
                         String[] foo() {
                             return 'ab'
                         }

                         String[] foox() {
                           return 2
                         }

                         int[] bar() {
                           <warning descr="Cannot return 'String' from method returning 'int[]'">return</warning> 'ab'
                         }
                         """);
  }

  @Test
  public void testAssignNullToPrimitiveTypesAndWrappers() {
    highlighting.get().doTestHighlighting("""
                         int <warning descr="Cannot assign 'null' to 'int'">x</warning> = null
                         double <warning descr="Cannot assign 'null' to 'double'">y</warning> = null
                         boolean a = null
                         Integer z = null
                         Boolean b = null
                         Integer i = null
                         """);
  }

  @Test
  public void testAssignNullToPrimitiveParameters() {
    highlighting.get().doTestHighlighting("""
                         def _int(int x) {}
                         def _boolean(boolean x) {}
                         def _Boolean(Boolean x) {}

                         _int<warning descr="'_int' in '_' cannot be applied to '(null)'">(null)</warning>
                         _boolean<warning descr="'_boolean' in '_' cannot be applied to '(null)'">(null)</warning>
                         _Boolean(null)
                         """);
  }

  @Test
  public void testInnerWarning() {
    highlighting.get().doTestHighlighting("""
                         public static void main(String[] args) {
                             bar <warning descr="'bar' in '_' cannot be applied to '(T)'">(foo(foo(foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('2')</warning>)))</warning>
                         }

                         static def <T extends Number> T foo(T abc) {
                             abc
                         }

                         static bar(String s) {

                         }
                         """);
  }

  @Test
  public void testLiteralConstructorWithNamedArgs() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.Immutable

                         @Immutable class Money {
                             String currency
                             int amount
                         }

                         Money d = [amount: 100, currency:'USA']

                         """);
  }

  @Test
  public void testBooleanIsAssignableToAny() {
    highlighting.get().doTestHighlighting("""
                               boolean b1 = new Object()
                               boolean b2 = null
                               Boolean b3 = new Object()
                               Boolean b4 = null
                         """);
  }

  @Test
  public void testArrayAccess() {
    highlighting.get().doTestHighlighting("""
                         int [] i = [1, 2]

                         print i[1]
                         print i[1, 2]
                         print i[1..2]
                         print i['a']
                         print i['a', 'b']
                         """);
  }

  @Test
  public void testArrayAccess2() {
    highlighting.get().doTestHighlighting("""
                         int[] i() { [1, 2] }

                         print i()[1]
                         print i()[1, 2]
                         print i()[1..2]
                         print i()['a']
                         print i()['a', 'b']
                         """);
  }

  @Test
  public void testArrayAccess3() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           def getAt(int x) {''}
                         }

                         X i() { new X() }

                         print i()[1]
                         print <weak_warning descr="Cannot infer argument types">i()<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning></weak_warning>
                         print <weak_warning descr="Cannot infer argument types">i()<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer..java.lang.Integer])'">[1..2]</warning></weak_warning>
                         print i()['a']
                         print <weak_warning descr="Cannot infer argument types">i()<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String])'">['a', 'b']</warning></weak_warning>
                         """);
  }

  @Test
  public void testArrayAccess4() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           def getAt(int x) {''}
                         }

                         X i = new X()

                         print i[1]
                         print <weak_warning descr="Cannot infer argument types">i<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning></weak_warning>
                         print <weak_warning descr="Cannot infer argument types">i<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer..java.lang.Integer])'">[1..2]</warning></weak_warning>
                         print i['a']
                         print <weak_warning descr="Cannot infer argument types">i<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String])'">['a', 'b']</warning></weak_warning>
                         """);
  }

  @Test
  public void testArrayAccess5() {
    highlighting.get().doTestHighlighting("""
                         print a<warning descr="'getAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning>
                         """);
  }

  @Test
  public void testArrayAccess6() {
    highlighting.get().doTestHighlighting("""
                         int[] i = [1, 2]

                         i[1] = 2
                         i<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
                         <warning descr="Cannot assign 'String' to 'int'">i[1]</warning> = 'a'
                         <warning descr="Cannot assign 'String' to 'int'">i['a']</warning> = 'b'
                         i<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
                         """);
  }

  @Test
  public void testArrayAccess7() {
    highlighting.get().doTestHighlighting("""
                         int[] i() { [1, 2] }

                         i()[1] = 2
                         i()<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
                         <warning descr="Cannot assign 'String' to 'int'">i()[1]</warning> = 'a'
                         <warning descr="Cannot assign 'String' to 'int'">i()['a']</warning> = 'b'
                         i()<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
                         """);
  }

  @Test
  public void testArrayAccess8() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           def putAt(int x, int y) {''}
                         }

                         X i() { new X() }

                         i()[1] = 2
                         i()<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
                         i()<warning descr="'putAt' in 'X' cannot be applied to '(java.lang.Integer, java.lang.String)'">[1]</warning> = 'a'
                         i()['a'] = 'b'
                         i()<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
                         """);
  }

  @Test
  public void testArrayAccess9() {
    highlighting.get().doTestHighlighting("""
                         class X {
                           def putAt(int x, int y) {''}
                         }

                         X i = new X()

                         i[1] = 2
                         i<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
                         i<warning descr="'putAt' in 'X' cannot be applied to '(java.lang.Integer, java.lang.String)'">[1]</warning> = 'a'
                         i['a'] = 'b'
                         i<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
                         """);
  }

  @Test
  public void testArrayAccess10() {
    highlighting.get().doTestHighlighting("""
                         a<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 3]</warning> = 2
                         """);
  }

  @Test
  public void testVarWithInitializer() {
    highlighting.get().doTestHighlighting("""
                         Object o = new Date()
                         foo(o)
                         bar<warning descr="'bar' in '_' cannot be applied to '(java.util.Date)'">(o)</warning>

                         def foo(Date d) {}
                         def bar(String s) {}
                         """);
  }

  @Test
  public void testClassTypesWithMadGenerics() {
    highlighting.get().doTestHighlighting("""
                         //no warnings are expected!

                         class CollectionTypeTest {
                             void implicitType() {
                                 def classes = [String, Integer]
                                 assert classNames(classes + [Double, Long]) == ['String', 'Integer', 'Double', 'Long'] // warning here
                                 assert classNames([Double, Long] + classes) == ['Double', 'Long', 'String', 'Integer']
                             }

                             void explicitInitType() {
                                 Collection<Class> classes = [String, Integer]
                                 assert classNames(classes + [Double, Long]) == ['String', 'Integer', 'Double', 'Long']
                                 assert classNames([Double, Long] + classes) == ['Double', 'Long', 'String', 'Integer'] // warning here
                             }

                             void explicitSumType() {
                                 Collection<Class> classes = [String, Integer]
                                 assert classNames(classes + [Double, Long]) == ['String', 'Integer', 'Double', 'Long']

                                 Collection<Class> var = [Double, Long] + classes
                                 assert classNames(var) == ['Double', 'Long', 'String', 'Integer']
                             }

                             private static Collection<String> classNames(Collection<Class> classes) {
                                return classes.collect { it.simpleName }
                             }
                         }
                         """);
  }

  @Test
  public void testParameterInitializerWithGenericType() {
    highlighting.get().doTestHighlighting("""
                         class PsiElement {}
                         class Foo extends PsiElement implements I {}

                         interface I {}

                         def <T extends PsiElement> T foo1(Class<T> <warning descr="Cannot assign 'Class<String>' to 'Class<? extends PsiElement>'">x</warning> = String ) {}
                         def <T extends PsiElement> T foo2(Class<T> x = PsiElement ) {}
                         def <T> T foo3(Class<T> x = PsiElement ) {}
                         def <T extends PsiElement & I> T foo4(Class<T> <warning descr="Cannot assign 'Class<PsiElement>' to 'Class<? extends PsiElement & I>'">x</warning> = PsiElement ) {}
                         def <T extends PsiElement & I> T foo5(Class<T> x = Foo ) {}
                         """);
  }

  @Test
  public void testFixVariableType() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         int <warning>x<caret>x</warning> = 'abc'
                         """);

    final IntentionAction intention = myFixture.findSingleIntention("Change variable");
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            String xx = 'abc'
                            """);
  }

  @Test
  public void testFixVariableType2() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    highlighting.get().doTestHighlighting("""
                         int xx = 5

                         <warning>x<caret>x</warning> = 'abc'
                         """);

    final IntentionAction intention = myFixture.findSingleIntention("Change variable");
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            String xx = 5

                            xx = 'abc'
                            """);
  }

  @Test
  public void testInnerClassConstructorDefault() { highlighting.get().doTest(); }

  @Test
  public void testInnerClassConstructorNoArg() { highlighting.get().doTest(); }

  @Test
  public void testInnerClassConstructorWithArg() { highlighting.get().doTest(); }

  @Test
  public void testInnerClassConstructorWithAnotherArg() { highlighting.get().doTest(); }

  @Test
  public void testClosureIsNotAssignableToSAMInGroovy2_1() {
    highlighting.get().doTestHighlighting("""
                         interface X {
                           def foo()
                         }

                         X <warning>x</warning> = {print 2}
                         """);
  }

  @Test
  public void testVoidMethodAssignability() {
    highlighting.get().doTestHighlighting("""
                         void foo() {}

                         def foo = foo()

                         def bar() {
                           foo() //no warning
                         }

                         def zoo() {
                           return foo()
                         }
                         """);
  }

  @Test
  public void testBinaryOperatorApplicability() {
    highlighting.get().doTestHighlighting("""
                         void bug(Collection<String> foo, Collection<String> bar) {
                             foo <warning descr="'leftShift' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.util.Collection<java.lang.String>)'"><<</warning> bar   // warning missed
                             foo << "a"
                         }""");
  }

  @Test
  public void testPlusIsApplicable() {
    highlighting.get().doTestHighlighting("""
                         print 1 + 2

                         print <weak_warning descr="Cannot infer argument types">4 <warning descr="'plus' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.util.ArrayList)'">+</warning> new ArrayList()</weak_warning>
                         """);
  }

  @Test
  public void testMultiAssignmentCS() {
    highlighting.get().doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def list = [1, 2]
                             def (a, b) = <error>list</error>
                         }
                         """);
  }

  @Test
  public void testMultiAssignmentWithTypeError() {
    highlighting.get().doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def list = ["", ""]
                             def (Integer a, b) = <error>list</error>
                         }
                         """);
  }

  @Test
  public void testMultiAssignmentLiteralWithTypeError() {
    highlighting.get().doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def (Integer <error>a</error>, b) = ["", ""]
                         }
                         """);
  }

  @Test
  public void testMultiAssignment() {
    highlighting.get().doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def (a, b) = [1, 2]
                         }
                         """);
  }

  @Test
  public void testRawListReturn() {
    highlighting.get().doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         List foo() {
                             return [""]
                         }
                         """);
  }

  @Test
  public void testOptionalArgumentOnCompileStatic() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class A {
                             A(String args) {}

                             def foo() {
                                 new A<error>()</error>
                             }
                         }
                         """);
  }

  @Test
  public void testOptionalVarargArgumentOnCompileStatic() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class A {
                             A(String... args) {}

                             def foo() {
                                 new A()
                             }
                         }
                         """);
  }

  @Test
  public void testOptionalClosureArgOnCompileStatic() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def method() {
                             Closure<String> cl = {"str"}
                             cl()
                         }
                         """);
  }

  @Test
  public void testStringTupleAssignment() {
    highlighting.get().doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class TestType {
                             static def bar(Object[] list) {
                                 def (String name, Integer matcherEnd) = [list[0], list[2] as Integer]
                             }
                         }
                         """);
  }

  @Test
  public void testUnknownArgumentPlus() {
    highlighting.get().doTestHighlighting("""
                         class A1{}

                         class E {
                             def m(){

                             }
                             def plus(A1 a1) {

                             }
                         }

                         new E() <weak_warning descr="Cannot infer argument types">+</weak_warning> a
                         """);
  }

  @Test
  public void testUnknownArgumentPlus2() {
    highlighting.get().doTestHighlighting("""
                         class A1{}
                         class A2{}

                         class E {
                             def m(){

                             }
                             def plus(A1 a1) {

                             }

                             def plus(A2 a2) {

                             }
                         }

                         new E() <weak_warning descr="Cannot infer argument types">+</weak_warning> a
                         """);
  }

  @Test
  public void testInapplicableWithUnknownArgument() {
    highlighting.get().doTestHighlighting("""
                         def foo(String s, int x) {}
                         def foo(String s, Object o) {}
                         def foo(String s, String x) {}

                         // second and third overloads are applicable;
                         // first overload is inapplicable independently of the first arg type;
                         foo<weak_warning descr="Cannot infer argument types">(unknown, "hi")</weak_warning>

                         // only second overload is applicable;
                         // because of that we don't highlight unknown args
                         foo(unknown, new Object())
                         """);
  }
}
