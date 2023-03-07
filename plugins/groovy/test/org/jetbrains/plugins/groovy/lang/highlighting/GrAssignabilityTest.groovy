// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyResultOfIncrementOrDecrementUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter

/**
 * @author Max Medvedev
 */
class GrAssignabilityTest extends GrHighlightingTestBase {
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  void testIncompatibleTypesAssignments() { doTest() }

  void testDefaultMapConstructorNamedArgs() {
    addBigDecimal()
    doTest(new GroovyConstructorNamedArgumentsInspection())
  }

  void testDefaultMapConstructorNamedArgsError() {
    addBigDecimal()
    doTest(new GroovyConstructorNamedArgumentsInspection())
  }

  void testDefaultMapConstructorWhenDefConstructorExists() {
    doTest(new GroovyConstructorNamedArgumentsInspection())
  }

  void testUnresolvedMethodCallWithTwoDeclarations() {
    doTest()
  }

  void testConstructor() {
    doTest(new GroovyConstructorNamedArgumentsInspection())
  }

  void testEverythingAssignableToString() { doTest() }

  void testMethodCallWithDefaultParameters() { doTest() }

  void testClosureWithDefaultParameters() { doTest() }

  void 'test method with default parameters and varargs'() {
    doTestHighlighting '''\
def go(String a, String b = 'b', String c, int ... i) {}
go('a', 'c', 1, 2, 3)
'''
  }

  void testClosureApplicability() { doTest() }

  void testSingleParameterMethodApplicability() { doTest() }

  void testCallIsNotApplicable() { doTest() }

  void testPathCallIsNotApplicable() { doTest() }

  void testByteArrayArgument() { doTest() }

  void testPutValueToEmptyMap() { doTest() }

  void _testPutIncorrectValueToMap() { doTest() } //incorrect test

  void testTupleTypeAssignments() {
    addBigDecimal()
    doTest()
  }

  void testSignatureIsNotApplicableToList() {
    doTest()
  }

  void testInheritConstructorsAnnotation() {
    doTest()
  }

  void testCollectionAssignments() { doTest() }

  void testReturnAssignability() { doTest() }

  void testMapNotAcceptedAsStringParameter() { doTest() }

  void testRawTypeInAssignment() { doTest() }

  void testMapParamWithNoArgs() { doTest() }

  void testInheritInterfaceInDelegate() {
    doTest()
  }

  void testThisTypeInStaticContext() {
    doTest()
  }

  void testAnonymousClassArgList() {
    doTest()
  }

  void testTupleConstructorAttributes() {
    doTest()
  }

  void testCanonicalConstructorApplicability() {
    myFixture.addClass("package groovy.transform; public @interface Canonical {}")
    doTest()
  }

  void testStringAssignableToChar() {
    doTest()
  }


  void testCurrying() {
    doTest()
  }

  void testAnotherCurrying() {
    doTest()
  }

  void testResultOfIncUsed() {
    doTest(new GroovyResultOfIncrementOrDecrementUsedInspection())
  }

  void testNativeMapAssignability() {
    doTest()
  }

  void testTwoLevelGrMap() {
    doTest()
  }

  void testPassingCollectionSubtractionIntoGenericMethod() {
    doTest(new GrUnresolvedAccessInspection())
  }

  void testImplicitEnumCoercion() {
    doTest()
  }

  void testUnknownVarInArgList() {
    doTest()
  }

  void testCallableProperty() {
    doTest(new GrUnresolvedAccessInspection())
  }

  void testEnumConstantConstructors() {
    doTest()
  }

  void testSpreadArguments() {
    doTest()
  }

  void testDiamondTypeInferenceSOE() {
    doTestHighlighting(''' Map<Integer, String> a; a[2] = [:] ''', false, false, false)
  }

  void _testThisInStaticMethodOfAnonymousClass() {
    doTestHighlighting('''\
class A {
    static abc
    def foo() {
        new Runnable() {
            <error descr="Inner classes cannot have static declarations">static</error> void run() {
                print abc
            }
        }.run()
    }
}''', true, false, false)
  }

  void testNonInferrableArgsOfDefParams() {
    def registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE)
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    try {
    doTestHighlighting('''\
def foo0(def a) { }
def bar0(def b) { foo0(b) }

def foo1(Object a) { }
def bar1(def b) { foo1(b) }

def foo2(String a) { }
def bar2(def b) { foo2(b) }
''')
    } finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue)
    }
  }

  void testPutAtApplicability() {
    myFixture.addClass("""\
package java.util;
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}
""")

    doTestHighlighting('''\
LinkedHashMap<File, List<File>> files = [:]
files[new File('a')] = [new File('b')]
files<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.io.File, java.io.File)'">[new File('a')]</warning> = new File('b')
''')
  }

  void testStringToCharAssignability() {
    doTestHighlighting('''\
def foo(char c){}

foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('a')</warning>
foo('a' as char)
foo('a' as Character)

char c = 'a'
''')
  }

  void testMethodRefs1() {
    doTestHighlighting('''\
class A {
  int foo(){2}

  Date foo(int x) {null}
}

def foo = new A().&foo

int i = foo()
int <warning descr="Cannot assign 'Date' to 'int'">i2</warning> = foo(2)
Date d = foo(2)
Date <warning descr="Cannot assign 'int' to 'Date'">d2</warning> = foo()
''')
  }

  void testMethodRefs2() {
    doTestHighlighting('''\
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
''')
  }

  void testThrowObject() {
    doTestHighlighting('''\
def foo() {
  throw new RuntimeException()
}
def bar () {
  <warning descr="Cannot assign 'Object' to 'Throwable'">throw</warning> new Object()
}

def test() {
  throw new Throwable()
}
''')
  }

  void testCategoryWithPrimitiveType() {
    doTestHighlighting('''\
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
''', GrUnresolvedAccessInspection)
  }

  void testCompileStaticWithAssignabilityCheck() {
    myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic {
}''')

    doTestHighlighting('''\
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
''')
  }

  void testClosuresInAnnotations() {
    doTestHighlighting('''\
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
''')
  }

  void testTupleAssignment() {
    doTestHighlighting('''\
String x 
int y 
(x, <warning descr="Cannot assign 'String' to 'int'">y</warning>) = foo()

print x + y

List<String> foo() {[]}
''')
  }

  void testTupleDeclaration() {
    doTestHighlighting('''\
def (int <warning descr="Cannot assign 'String' to 'int'">x</warning>, String y) = foo()

List<String> foo() {[]}
''')
  }

  void testCastClosureToInterface() {
    doTestHighlighting('''\
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

''')
  }

  void testVarargsWithoutTypeName() {
    doTestHighlighting('''\
def foo(String key, ... params) {

}

foo('anc')
foo('abc', 1, '')
foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.Integer)'">(5)</warning>
''')
  }

  void testIncorrectReturnValue() {
    doTestHighlighting('''\
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
''')
  }


  void testForInAssignability() {
    doTestHighlighting('''\
for (<warning descr="Cannot assign 'String' to 'int'">int x</warning> in ['a']){}
''')
  }

  void testAssignabilityOfMethodProvidedByCategoryAnnotation() {
    doTestHighlighting('''\
@Category(List)
class EvenSieve {
    def getNo2() {
        removeAll { 4 % 2 == 0 } //correct access
        add<warning descr="'add' in 'java.util.List<E>' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>
    }
}
''')
  }

  void testAssignabilityOfCategoryMethod() {
    doTestHighlighting('''
class Cat {
  static foo(Class c, int x) {}
}

class X {}

use(Cat) {
  X.foo(1)
}

''')
  }

  void testImplicitConversionToArray() {
    doTestHighlighting('''\
String[] foo() {
    return 'ab'
}

String[] foox() {
  return 2
}

int[] bar() {
  <warning descr="Cannot return 'String' from method returning 'int[]'">return</warning> 'ab'
}
''')
  }

  void testAssignNullToPrimitiveTypesAndWrappers() {
    doTestHighlighting('''\
int <warning descr="Cannot assign 'null' to 'int'">x</warning> = null
double <warning descr="Cannot assign 'null' to 'double'">y</warning> = null
boolean a = null
Integer z = null
Boolean b = null
Integer i = null
''')
  }

  void testAssignNullToPrimitiveParameters() {
    doTestHighlighting('''\
def _int(int x) {}
def _boolean(boolean x) {}
def _Boolean(Boolean x) {}

_int<warning descr="'_int' in '_' cannot be applied to '(null)'">(null)</warning>
_boolean<warning descr="'_boolean' in '_' cannot be applied to '(null)'">(null)</warning>
_Boolean(null)
''')
  }

  void testInnerWarning() {
    doTestHighlighting('''\
public static void main(String[] args) {
    bar <warning descr="'bar' in '_' cannot be applied to '(T)'">(foo(foo(foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('2')</warning>)))</warning>
}

static def <T extends Number> T foo(T abc) {
    abc
}

static bar(String s) {

}
''')
  }

  void testLiteralConstructorWithNamedArgs() {
    doTestHighlighting('''\
import groovy.transform.Immutable

@Immutable class Money {
    String currency
    int amount
}

Money d = [amount: 100, currency:'USA']

''')
  }

  void testBooleanIsAssignableToAny() {
    doTestHighlighting('''\
      boolean b1 = new Object()
      boolean b2 = null
      Boolean b3 = new Object()
      Boolean b4 = null
''')
  }

  void testArrayAccess() {
    doTestHighlighting('''\
int [] i = [1, 2]

print i[1]
print i[1, 2]
print i[1..2]
print i['a']
print i['a', 'b']
''')
  }

  void testArrayAccess2() {
    doTestHighlighting('''\
int[] i() { [1, 2] }

print i()[1]
print i()[1, 2]
print i()[1..2]
print i()['a']
print i()['a', 'b']
''')
  }

  void testArrayAccess3() {
    doTestHighlighting('''\
class X {
  def getAt(int x) {''}
}

X i() { new X() }

print i()[1]
print <weak_warning descr="Cannot infer argument types">i()<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning></weak_warning>
print <weak_warning descr="Cannot infer argument types">i()<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer..java.lang.Integer])'">[1..2]</warning></weak_warning>
print i()['a']
print <weak_warning descr="Cannot infer argument types">i()<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String])'">['a', 'b']</warning></weak_warning>
''')
  }

  void testArrayAccess4() {
    doTestHighlighting('''\
class X {
  def getAt(int x) {''}
}

X i = new X()

print i[1]
print <weak_warning descr="Cannot infer argument types">i<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning></weak_warning>
print <weak_warning descr="Cannot infer argument types">i<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.Integer..java.lang.Integer])'">[1..2]</warning></weak_warning>
print i['a']
print <weak_warning descr="Cannot infer argument types">i<warning descr="'getAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String])'">['a', 'b']</warning></weak_warning>
''')
  }

  void testArrayAccess5() {
    doTestHighlighting('''\
print a<warning descr="'getAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer])'">[1, 2]</warning>
''')
  }

  void testArrayAccess6() {
    doTestHighlighting('''\
int[] i = [1, 2]

i[1] = 2
i<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
<warning descr="Cannot assign 'String' to 'int'">i[1]</warning> = 'a'
<warning descr="Cannot assign 'String' to 'int'">i['a']</warning> = 'b'
i<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
''')
  }

  void testArrayAccess7() {
    doTestHighlighting('''\
int[] i() { [1, 2] }

i()[1] = 2
i()<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
<warning descr="Cannot assign 'String' to 'int'">i()[1]</warning> = 'a'
<warning descr="Cannot assign 'String' to 'int'">i()['a']</warning> = 'b'
i()<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
''')
  }

  void testArrayAccess8() {
    doTestHighlighting('''\
class X {
  def putAt(int x, int y) {''}
}

X i() { new X() }

i()[1] = 2
i()<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
i()<warning descr="'putAt' in 'X' cannot be applied to '(java.lang.Integer, java.lang.String)'">[1]</warning> = 'a'
i()['a'] = 'b'
i()<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
''')
  }

  void testArrayAccess9() {
    doTestHighlighting('''\
class X {
  def putAt(int x, int y) {''}
}

X i = new X()

i[1] = 2
i<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 2]</warning> = 2
i<warning descr="'putAt' in 'X' cannot be applied to '(java.lang.Integer, java.lang.String)'">[1]</warning> = 'a'
i['a'] = 'b'
i<warning descr="'putAt' in 'X' cannot be applied to '([java.lang.String, java.lang.String], java.lang.Integer)'">['a', 'b']</warning> = 1
''')
  }

  void testArrayAccess10() {
    doTestHighlighting('''\
a<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '([java.lang.Integer, java.lang.Integer], java.lang.Integer)'">[1, 3]</warning> = 2
''')
  }

  void testVarWithInitializer() {
    doTestHighlighting('''\
Object o = new Date()
foo(o)
bar<warning descr="'bar' in '_' cannot be applied to '(java.util.Date)'">(o)</warning>

def foo(Date d) {}
def bar(String s) {}
''')
  }

  void testClassTypesWithMadGenerics() {
    doTestHighlighting('''\
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
''')
  }

  void testParameterInitializerWithGenericType() {
    doTestHighlighting('''\
class PsiElement {}
class Foo extends PsiElement implements I {}

interface I {}

def <T extends PsiElement> T foo1(Class<T> <warning descr="Cannot assign 'Class<String>' to 'Class<? extends PsiElement>'">x</warning> = String ) {}
def <T extends PsiElement> T foo2(Class<T> x = PsiElement ) {}
def <T> T foo3(Class<T> x = PsiElement ) {}
def <T extends PsiElement & I> T foo4(Class<T> <warning descr="Cannot assign 'Class<PsiElement>' to 'Class<? extends PsiElement & I>'">x</warning> = PsiElement ) {}
def <T extends PsiElement & I> T foo5(Class<T> x = Foo ) {}
''')
  }

  void testFixVariableType() {
    doTestHighlighting('''\
int <warning>x<caret>x</warning> = 'abc'
''')


    final IntentionAction intention = myFixture.findSingleIntention('Change variable')
    myFixture.launchAction(intention)
    myFixture.checkResult('''\
String xx = 'abc'
''')

  }

  void testFixVariableType2() {
    doTestHighlighting('''\
int xx = 5

<warning>x<caret>x</warning> = 'abc'
''')

    final IntentionAction intention = myFixture.findSingleIntention('Change variable')
    myFixture.launchAction(intention)
    myFixture.checkResult('''\
String xx = 5

xx = 'abc'
''')
  }

  void testInnerClassConstructorDefault() { doTest() }

  void testInnerClassConstructorNoArg() { doTest() }

  void testInnerClassConstructorWithArg() { doTest() }

  void testInnerClassConstructorWithAnotherArg() { doTest() }

  void testClosureIsNotAssignableToSAMInGroovy2_1() {
    doTestHighlighting('''\
interface X {
  def foo()
}

X <warning>x</warning> = {print 2}
''')
  }

  void testVoidMethodAssignability() {
    doTestHighlighting('''\
void foo() {}

def foo = foo()

def bar() {
  foo() //no warning
}

def zoo() {
  return foo()
}
''')
  }

  void testBinaryOperatorApplicability() {
    doTestHighlighting('''\
void bug(Collection<String> foo, Collection<String> bar) {
    foo <warning descr="'leftShift' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.util.Collection<java.lang.String>)'"><<</warning> bar   // warning missed
    foo << "a"
}''')
  }

  void testPlusIsApplicable() {
    doTestHighlighting('''\
print 1 + 2

print <weak_warning descr="Cannot infer argument types">4 <warning descr="'plus' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.util.ArrayList)'">+</warning> new ArrayList()</weak_warning>
''')
  }

  void testMultiAssignmentCS() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def list = [1, 2]
    def (a, b) = <error>list</error>
}
'''
  }

  void testMultiAssignmentWithTypeError() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def list = ["", ""]
    def (Integer a, b) = <error>list</error>
}
'''
  }

  void testMultiAssignmentLiteralWithTypeError() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def (Integer <error>a</error>, b) = ["", ""]
}
'''
  }

  void testMultiAssignment() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def (a, b) = [1, 2]
}
'''
  }

  void testRawListReturn() {
    doTestHighlighting '''
import groovy.transform.CompileStatic

@CompileStatic
List foo() {
    return [""]
}
'''
  }

  void 'test optional argument on CompileStatic'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
class A {
    A(String args) {}

    def foo() {
        new A<error>()</error>
    }
}
'''
  }

  void 'test optional vararg argument on CompileStatic'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
class A {
    A(String... args) {}

    def foo() {
        new A()
    }
}
'''
  }

  void 'test optional closure arg on CompileStatic'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
def method() {
    Closure<String> cl = {"str"}
    cl()
}
'''
  }

  void 'test string tuple assignment'() {
    doTestHighlighting '''\
import groovy.transform.CompileStatic

@CompileStatic
class TestType {
    static def bar(Object[] list) {
        def (String name, Integer matcherEnd) = [list[0], list[2] as Integer]
    }
}
'''
  }

  void 'test unknown argument plus'() {
    doTestHighlighting '''
class A1{}

class E {
    def m(){

    }
    def plus(A1 a1) {

    }
}

new E() <weak_warning descr="Cannot infer argument types">+</weak_warning> a
'''
  }

  void 'test unknown argument plus 2'() {
    doTestHighlighting '''
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
'''
  }

  void 'test inapplicable with unknown argument'() {
    doTestHighlighting '''\
def foo(String s, int x) {}
def foo(String s, Object o) {}
def foo(String s, String x) {}

// second and third overloads are applicable;
// first overload is inapplicable independently of the first arg type;
foo<weak_warning descr="Cannot infer argument types">(unknown, "hi")</weak_warning>

// only second overload is applicable;
// because of that we don't highlight unknown args
foo(unknown, new Object())  
'''
  }
}
