/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyResultOfIncrementOrDecrementUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

/**
 * @author Max Medvedev
 */
class GrAssignabilityTest extends GrHighlightingTestBase {
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  public void testIncompatibleTypesAssignments() { doTest(); }

  public void testDefaultMapConstructorNamedArgs() {
    doTest(new GroovyConstructorNamedArgumentsInspection());
  }

  public void testDefaultMapConstructorNamedArgsError() {
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

  public void testEverythingAssignableToString() {doTest();}

  public void testMethodCallWithDefaultParameters() {doTest();}

  public void testClosureWithDefaultParameters() {doTest();}

  public void testClosureCallMethodWithInapplicableArguments() {doTest();}

  public void testCallIsNotApplicable() {doTest();}

  public void testPathCallIsNotApplicable() {doTest();}

  public void testByteArrayArgument() {doTest();}

  public void testPutValueToEmptyMap() {doTest();}

  public void _testPutIncorrectValueToMap() {doTest();} //incorrect test

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

  public void testCollectionAssignments() {doTest(); }

  public void testReturnAssignability() {doTest(); }

  public void testMapNotAcceptedAsStringParameter() {doTest();}

  public void testRawTypeInAssignment() {doTest();}

  public void testMapParamWithNoArgs() {doTest();}

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
    doTest();
  }

  public void testEnumConstantConstructors() {
    doTest();
  }

  public void testLiteralConstructorUsages() {
    doTest();
  }

  public void testSpreadArguments() {
    doTest();
  }

  public void testDiamondTypeInferenceSOE() {
    testHighlighting(''' Map<Integer, String> a; a[2] = [:] ''', false, false, false)
  }

  void _testThisInStaticMethodOfAnonymousClass() {
    testHighlighting('''\
class A {
    static abc
    def foo() {
        new Runnable() {
            <error descr="Inner classes cannot have static declarations">static</error> void run() {
                print abc
            }
        }.run()
    }
}''', true, false, false);
  }

  public void testNonInferrableArgsOfDefParams() {
    testHighlighting('''\
def foo0(def a) { }
def bar0(def b) { foo0(b) }

def foo1(Object a) { }
def bar1(def b) { foo1(b) }

def foo2(String a) { }
def bar2(def b) { foo2<weak_warning descr="Cannot infer argument types">(b)</weak_warning> }
''')
  }

  public void testPutAtApplicability() {
    myFixture.addClass("""\
package java.util;
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}
""")

    testHighlighting('''\
LinkedHashMap<File, List<File>> files = [:]
files[new File('a')] = [new File('b')]
files<warning descr="'putAt' in 'org.codehaus.groovy.runtime.DefaultGroovyMethods' cannot be applied to '(java.io.File, java.io.File)'">[new File('a')]</warning> = new File('b')
''')
  }

  public void testStringToCharAssignability() {
    testHighlighting('''\
def foo(char c){}

foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.String)'">('a')</warning>
foo('a' as char)
foo('a' as Character)

char c = 'a'
''')
  }

  void testMethodRefs1() {
    testHighlighting('''\
class A {
  int foo(){2}

  Date foo(int x) {null}
}

def foo = new A().&foo

int i = foo()
int i2 = <warning descr="Cannot assign 'Date' to 'int'">foo(2)</warning>
Date d = foo(2)
Date d2 = <warning descr="Cannot assign 'Integer' to 'Date'">foo()</warning>
''')
  }

  void testMethodRefs2() {
    testHighlighting('''\
class Bar {
  def foo(int i, String s2) {s2}
  def foo(int i, int i2) {i2}
}

def cl = new Bar<error descr="'(' expected">.</error>&foo
cl = cl.curry(1)
String s = cl("2")
int s2 = <warning descr="Cannot assign 'String' to 'int'">cl("2")</warning>
int i = cl(3)
String i2 = cl(3)
''')
  }

  void testThrowObject() {
    testHighlighting('''\
def foo() {
  throw new RuntimeException()
}
def bar () {
  throw <warning descr="Cannot assign 'Object' to 'Throwable'">new Object()</warning>
}

def test() {
  throw new Throwable()
}
''')
  }

  void testCategoryWithPrimitiveType() {
    testHighlighting('''\
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
  1.<warning descr="Category method 'foo' cannot be applied to 'java.lang.Integer'">foo</warning>()
  (1 as int).<warning descr="Category method 'foo' cannot be applied to 'java.lang.Integer'">foo</warning>()
}
''')
  }

  void testCompileStaticWithAssignabilityCheck() {
    myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic {
}''')

    testHighlighting('''\
import groovy.transform.CompileStatic

class A {

  def foo(String s) {
    int x = <warning descr="Cannot assign 'Date' to 'int'">new Date()</warning>
  }

  @CompileStatic
  def bar() {
    int x = <error descr="Cannot assign 'Date' to 'int'">new Date()</error>
  }
}
''')
  }

  void testClosuresInAnnotations() {
    testHighlighting('''\
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
    testHighlighting('''\
def (String x, int y)
(x, <warning descr="Cannot assign 'String' to 'Integer'">y</warning>) = foo()

print x + y

List<String> foo() {[]}
''')
  }

  void testTupleDeclaration() {
    testHighlighting('''\
def (int <warning descr="Cannot assign 'String' to 'int'">x</warning>, String y) = foo()

List<String> foo() {[]}
''')
  }

  void testCastClosureToInterface() {
    testHighlighting('''\
interface Function<D, F> {
    F fun(D d)
}

def foo(Function<String, String> function) {
 //   print function.fun('abc')
}


foo<warning descr="'foo' in '_' cannot be applied to '(Function<java.lang.Double,java.lang.Double>)'">({println  it.byteValue()} as Function<Double, Double>)</warning>
foo({println  it.substring(1)} as Function)
foo({println  it.substring(1)} as Function<String, String>)
foo<warning descr="'foo' in '_' cannot be applied to '(groovy.lang.Closure<java.lang.Void>)'">({println  it})</warning>

''')
  }

  void testVarargsWithoutTypeName() {
    testHighlighting('''\
def foo(String key, ... params) {

}

foo('anc')
foo('abc', 1, '')
foo<warning descr="'foo' in '_' cannot be applied to '(java.lang.Integer)'">(5)</warning>
''')
  }

  void testIncorrectReturnValue() {
    testHighlighting('''\
private int getObjects() {
    try {
        def t = "test";
        t.substring(0);
    }
    finally {
        //...
    }

    return <warning descr="Cannot assign 'String' to 'int'">''</warning>;
}
''')
  }


  void testForInAssignability() {
    testHighlighting('''\
for (int <warning descr="Cannot assign 'String' to 'int'">x</warning> in ['a']){}
''')
  }

  void testAssignabilityOfMethodProvidedByCategoryAnnotation() {
    testHighlighting('''\
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
    testHighlighting('''
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
    testHighlighting('''\
String[] foo() {
    return 'ab'
}

String[] foox() {
  return 2
}

int[] bar() {
  return <warning descr="Cannot assign 'String' to 'int[]'">'ab'</warning>
}
''')
  }

  void testAssignNullToPrimitiveTypesAndWrappers() {
    testHighlighting('''\
int x = <warning descr="Cannot assign 'null' to 'int'">null</warning>
double y = <warning descr="Cannot assign 'null' to 'double'">null</warning>
Integer z = null
boolean a = <warning descr="Cannot assign 'null' to 'boolean'">null</warning>
Boolean b = null
''')
  }

  void testAssignNullToPrimitiveParameters() {
    testHighlighting('''\
def _int(int x) {}
def _boolean(boolean x) {}
def _Boolean(Boolean x) {}

_int<warning descr="'_int' in '_' cannot be applied to '(null)'">(null)</warning>
_boolean<warning descr="'_boolean' in '_' cannot be applied to '(null)'">(null)</warning>
_Boolean(null)
''')
  }
}
