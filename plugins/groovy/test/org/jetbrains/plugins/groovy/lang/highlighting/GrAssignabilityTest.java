// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyResultOfIncrementOrDecrementUsedInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;

/**
 * @author Max Medvedev
 */
public class GrAssignabilityTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() {
    return new GroovyAssignabilityCheckInspection[]{new GroovyAssignabilityCheckInspection()};
  }

  public void testIncompatibleTypesAssignments() { doTest(); }

  public void testDefaultMapConstructorNamedArgs() {
    addBigDecimal();
    doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  public void testDefaultMapConstructorNamedArgsError() {
    addBigDecimal();
    doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  public void testDefaultMapConstructorWhenDefConstructorExists() {
    doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  public void testUnresolvedMethodCallWithTwoDeclarations() {
    doTest();
  }

  public void testConstructor() {
    doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  public void testEverythingAssignableToString() { doTest(); }

  public void testMethodCallWithDefaultParameters() { doTest(); }

  public void testClosureWithDefaultParameters() { doTest(); }

  public void testMethodWithDefaultParametersAndVarargs() {
    doTestHighlighting("""
                         def go(String a, String b = 'b', String c, int ... i) {}
                         go('a', 'c', 1, 2, 3)
                         """);
  }

  public void testClosureApplicability() { doTest(); }

  public void testSingleParameterMethodApplicability() { doTest(); }

  public void testCallIsNotApplicable() { doTest(); }

  public void testPathCallIsNotApplicable() { doTest(); }

  public void testByteArrayArgument() { doTest(); }

  public void testPutValueToEmptyMap() { doTest(); }

  public void _testPutIncorrectValueToMap() { doTest(); }

  public void testTupleTypeAssignments() {
    addBigDecimal();
    doTest();
  }

  public void testSignatureIsNotApplicableToList() {
    doTest();
  }

  public void testInheritConstructorsAnnotation() {
    doTest();
  }

  public void testCollectionAssignments() { doTest(); }

  public void testReturnAssignability() { doTest(); }

  public void testMapNotAcceptedAsStringParameter() { doTest(); }

  public void testRawTypeInAssignment() { doTest(); }

  public void testMapParamWithNoArgs() { doTest(); }

  public void testInheritInterfaceInDelegate() {
    doTest();
  }

  public void testThisTypeInStaticContext() {
    doTest();
  }

  public void testAnonymousClassArgList() {
    doTest();
  }

  public void testTupleConstructorAttributes() {
    doTest();
  }

  public void testCanonicalConstructorApplicability() {
    myFixture.addClass("package groovy.transform; public @interface Canonical {}");
    doTest();
  }

  public void testStringAssignableToChar() {
    doTest();
  }

  public void testCurrying() {
    doTest();
  }

  public void testAnotherCurrying() {
    doTest();
  }

  public void testResultOfIncUsed() {
    doTest(new GroovyResultOfIncrementOrDecrementUsedInspection());
  }

  public void testNativeMapAssignability() {
    doTest();
  }

  public void testTwoLevelGrMap() {
    doTest();
  }

  public void testPassingCollectionSubtractionIntoGenericMethod() {
    doTest(new GrUnresolvedAccessInspection());
  }

  public void testImplicitEnumCoercion() {
    doTest();
  }

  public void testUnknownVarInArgList() {
    doTest();
  }

  public void testCallableProperty() {
    doTest(new GrUnresolvedAccessInspection());
  }

  public void testEnumConstantConstructors() {
    doTest();
  }

  public void testSpreadArguments() {
    doTest();
  }

  public void testDiamondTypeInferenceSOE() {
    doTestHighlighting("""
                         Map<Integer, String> a; a[2] = [:]""", false, false, false);
  }

  public void _testThisInStaticMethodOfAnonymousClass() {
    doTestHighlighting("""
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

  public void testNonInferrableArgsOfDefParams() {
    boolean registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE);
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    try {
      doTestHighlighting("""
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

  public void testPutAtApplicability() {
    myFixture.addClass("""
                          package java.util;
                          public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}
                          """);

    doTestHighlighting("""
                         LinkedHashMap<File, List<File>> files = [:]
                         files[new File('a')] = [new File('b')]
                         files<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.io.File, java.io.File)'">[new File('a')]</warning> = new File('b')
                         """);
  }

  public void testStringToCharAssignability() {
    doTestHighlighting("""
                         def foo(char c){}

                         foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('a')</warning>
                         foo('a' as char)
                         foo('a' as Character)

                         char c = 'a'
                         """);
  }

  public void testMethodRefs1() {
    doTestHighlighting("""
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

  public void testMethodRefs2() {
    doTestHighlighting("""
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

  public void testThrowObject() {
    doTestHighlighting("""
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

  public void testCategoryWithPrimitiveType() {
    doTestHighlighting("""
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

  public void testCompileStaticWithAssignabilityCheck() {
    myFixture.addClass("""
                          package groovy.transform;
                          public @interface CompileStatic {
                          }""");

    doTestHighlighting("""
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

  public void testClosuresInAnnotations() {
    doTestHighlighting("""
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

  public void testTupleAssignment() {
    doTestHighlighting("""
                         String x
                         int y
                         (x, <warning descr="Cannot assign 'String' to 'int'">y</warning>) = foo()

                         print x + y

                         List<String> foo() {[]}
                         """);
  }

  public void testTupleDeclaration() {
    doTestHighlighting("""
                         def (int <warning descr="Cannot assign 'String' to 'int'">x</warning>, String y) = foo()

                         List<String> foo() {[]}
                         """);
  }

  public void testCastClosureToInterface() {
    doTestHighlighting("""
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

  public void testVarargsWithoutTypeName() {
    doTestHighlighting("""
                         def foo(String key, ... params) {

                         }

                         foo('anc')
                         foo('abc', 1, '')
                         foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.Integer)'">(5)</warning>
                         """);
  }

  public void testIncorrectReturnValue() {
    doTestHighlighting("""
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

  public void testForInAssignability() {
    doTestHighlighting("""
                         for (<warning descr="Cannot assign 'String' to 'int'">int x</warning> in ['a']){}
                         """);
  }

  public void testAssignabilityOfMethodProvidedByCategoryAnnotation() {
    doTestHighlighting("""
                         @Category(List)
                         class EvenSieve {
                             def getNo2() {
                                 removeAll { 4 % 2 == 0 } //correct access
                                 add<warning descr="'add' in 'java.util.List<E>' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>
                             }
                         }
                         """);
  }

  public void testAssignabilityOfCategoryMethod() {
    doTestHighlighting("""

                         class Cat {
                           static foo(Class c, int x) {}
                         }

                         class X {}

                         use(Cat) {
                           X.foo(1)
                         }

                         """);
  }

  public void testImplicitConversionToArray() {
    doTestHighlighting("""
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

  public void testAssignNullToPrimitiveTypesAndWrappers() {
    doTestHighlighting("""
                         int <warning descr="Cannot assign 'null' to 'int'">x</warning> = null
                         double <warning descr="Cannot assign 'null' to 'double'">y</warning> = null
                         boolean a = null
                         Integer z = null
                         Boolean b = null
                         Integer i = null
                         """);
  }

  public void testAssignNullToPrimitiveParameters() {
    doTestHighlighting("""
                         def _int(int x) {}
                         def _boolean(boolean x) {}
                         def _Boolean(Boolean x) {}

                         _int<warning descr="'_int' in '_' cannot be applied to '(null)'">(null)</warning>
                         _boolean<warning descr="'_boolean' in '_' cannot be applied to '(null)'">(null)</warning>
                         _Boolean(null)
                         """);
  }

  public void testInnerWarning() {
    doTestHighlighting("""
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

  public void testLiteralConstructorWithNamedArgs() {
    doTestHighlighting("""
                         import groovy.transform.Immutable

                         @Immutable class Money {
                             String currency
                             int amount
                         }

                         Money d = [amount: 100, currency:'USA']

                         """);
  }

  public void testBooleanIsAssignableToAny() {
    doTestHighlighting("""
                               boolean b1 = new Object()
                               boolean b2 = null
                               Boolean b3 = new Object()
                               Boolean b4 = null
                         """);
  }

  public void testArrayAccess() {
    doTestHighlighting("""
                         int [] i = [1, 2]

                         print i[1]
                         print i[1, 2]
                         print i[1..2]
                         print i['a']
                         print i['a', 'b']
                         """);
  }

  public void testArrayAccess2() {
    doTestHighlighting("""
                         int[] i() { [1, 2] }

                         print i()[1]
                         print i()[1, 2]
                         print i()[1..2]
                         print i()['a']
                         print i()['a', 'b']
                         """);
  }

  public void testArrayAccess3() {
    doTestHighlighting("""
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

  public void testArrayAccess4() {
    doTestHighlighting("""
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

  public void testArrayAccess5() {
    doTestHighlighting("""
                         print a<warning descr="'getAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning>
                         """);
  }

  public void testArrayAccess6() {
    doTestHighlighting("""
                         int[] i = [1, 2]

                         i[1] = 2
                         i<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
                         <warning descr="Cannot assign 'String' to 'int'">i[1]</warning> = 'a'
                         <warning descr="Cannot assign 'String' to 'int'">i['a']</warning> = 'b'
                         i<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
                         """);
  }

  public void testArrayAccess7() {
    doTestHighlighting("""
                         int[] i() { [1, 2] }

                         i()[1] = 2
                         i()<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
                         <warning descr="Cannot assign 'String' to 'int'">i()[1]</warning> = 'a'
                         <warning descr="Cannot assign 'String' to 'int'">i()['a']</warning> = 'b'
                         i()<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
                         """);
  }

  public void testArrayAccess8() {
    doTestHighlighting("""
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

  public void testArrayAccess9() {
    doTestHighlighting("""
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

  public void testArrayAccess10() {
    doTestHighlighting("""
                         a<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 3]</warning> = 2
                         """);
  }

  public void testVarWithInitializer() {
    doTestHighlighting("""
                         Object o = new Date()
                         foo(o)
                         bar<warning descr="'bar' in '_' cannot be applied to '(java.util.Date)'">(o)</warning>

                         def foo(Date d) {}
                         def bar(String s) {}
                         """);
  }

  public void testClassTypesWithMadGenerics() {
    doTestHighlighting("""
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

  public void testParameterInitializerWithGenericType() {
    doTestHighlighting("""
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

  public void testFixVariableType() {
    doTestHighlighting("""
                         int <warning>x<caret>x</warning> = 'abc'
                         """);


    final IntentionAction intention = myFixture.findSingleIntention("Change variable");
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            String xx = 'abc'
                            """);
  }

  public void testFixVariableType2() {
    doTestHighlighting("""
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

  public void testInnerClassConstructorDefault() { doTest(); }

  public void testInnerClassConstructorNoArg() { doTest(); }

  public void testInnerClassConstructorWithArg() { doTest(); }

  public void testInnerClassConstructorWithAnotherArg() { doTest(); }

  public void testClosureIsNotAssignableToSAMInGroovy2_1() {
    doTestHighlighting("""
                         interface X {
                           def foo()
                         }

                         X <warning>x</warning> = {print 2}
                         """);
  }

  public void testVoidMethodAssignability() {
    doTestHighlighting("""
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

  public void testBinaryOperatorApplicability() {
    doTestHighlighting("""
                         void bug(Collection<String> foo, Collection<String> bar) {
                             foo <warning descr="'leftShift' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.util.Collection<java.lang.String>)'"><<</warning> bar   // warning missed
                             foo << "a"
                         }""");
  }

  public void testPlusIsApplicable() {
    doTestHighlighting("""
                         print 1 + 2

                         print <weak_warning descr="Cannot infer argument types">4 <warning descr="'plus' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.util.ArrayList)'">+</warning> new ArrayList()</weak_warning>
                         """);
  }

  public void testMultiAssignmentCS() {
    doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def list = [1, 2]
                             def (a, b) = <error>list</error>
                         }
                         """);
  }

  public void testMultiAssignmentWithTypeError() {
    doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def list = ["", ""]
                             def (Integer a, b) = <error>list</error>
                         }
                         """);
  }

  public void testMultiAssignmentLiteralWithTypeError() {
    doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def (Integer <error>a</error>, b) = ["", ""]
                         }
                         """);
  }

  public void testMultiAssignment() {
    doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def foo() {
                             def (a, b) = [1, 2]
                         }
                         """);
  }

  public void testRawListReturn() {
    doTestHighlighting("""

                         import groovy.transform.CompileStatic

                         @CompileStatic
                         List foo() {
                             return [""]
                         }
                         """);
  }

  public void testOptionalArgumentOnCompileStatic() {
    doTestHighlighting("""
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

  public void testOptionalVarargArgumentOnCompileStatic() {
    doTestHighlighting("""
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

  public void testOptionalClosureArgOnCompileStatic() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         def method() {
                             Closure<String> cl = {"str"}
                             cl()
                         }
                         """);
  }

  public void testStringTupleAssignment() {
    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         @CompileStatic
                         class TestType {
                             static def bar(Object[] list) {
                                 def (String name, Integer matcherEnd) = [list[0], list[2] as Integer]
                             }
                         }
                         """);
  }

  public void testUnknownArgumentPlus() {
    doTestHighlighting("""
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

  public void testUnknownArgumentPlus2() {
    doTestHighlighting("""
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

  public void testInapplicableWithUnknownArgument() {
    doTestHighlighting("""
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
