// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiImmediateClassType
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

import static com.intellij.psi.CommonClassNames.*
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.allowNestedContext
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.allowNestedContextOnce

/**
 * @author ven
 */
@CompileStatic
class TypeInferenceTest extends TypeInferenceTestBase {

  void testTryFinallyFlow() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow/A.groovy").element
    final PsiType type = ref.type
    assertTrue(type instanceof PsiIntersectionType)
    final PsiType[] conjuncts = ((PsiIntersectionType)type).conjuncts
    assertEquals(conjuncts.length, 2)
  }

  void testTryFinallyFlow1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow1/A.groovy").element
    final PsiType type = ref.type
    assertNotNull(type)
    assertTrue(type.equalsToText("java.lang.Integer"))
  }

  void testTryFinallyFlow2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow2/A.groovy").element
    final PsiType type = ref.type
    assertNotNull(type)
    assertTrue(type.equalsToText("java.lang.Integer"))
  }

  void testThrowVariable() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("throwVariable/A.groovy").element
    final PsiType type = ref.type
    assertNotNull(type)
    assertEquals("java.lang.Exception", type.canonicalText)
  }

  void testGrvy852() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("grvy852/A.groovy").element
    final PsiType type = ref.type
    assertNotNull(type)
    assertEquals("java.lang.Object", type.canonicalText)
  }

  void testGenericMethod() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericMethod/A.groovy").element
    final PsiType type = ref.type
    assertNotNull(type)
    assertEquals("java.util.List<java.lang.String>", type.canonicalText)
  }

  void testCircular() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("circular/A.groovy").element
    assertNull(ref.type)
  }

  void testCircular1() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("circular1/A.groovy").element
    assertNull(ref.type)
  }

  void testClosure() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure/A.groovy").element
    assertNotNull(ref.type)
  }

  void testClosure1() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure1/A.groovy").element
    assertTrue(ref.type.equalsToText("java.lang.Integer"))
  }

  void testClosure2() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure2/A.groovy").element
    assertTrue(ref.type.equalsToText("int"))
  }

  void 'test binding from inside closure'() {
    doTest "list = ['a', 'b']; list.each { <caret>it }", JAVA_LANG_STRING
  }

  void testGrvy1209() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("grvy1209/A.groovy").element
    assertTrue(ref.type.equalsToText("java.lang.String"))
  }

  void testLeastUpperBoundClosureType() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("leastUpperBoundClosureType/A.groovy").element
    assertInstanceOf(ref.type, PsiImmediateClassType.class)
  }

  void testJavaLangClassType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("javaLangClassType/A.groovy").element
    assertEquals("java.lang.String", ref.type.canonicalText)
  }

  void testGenericWildcard() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericWildcard/A.groovy").element
    assertEquals("X<Base>", ref.type.canonicalText)
  }

  void testArrayLikeAccessWithIntSequence() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayLikeAccessWithIntSequence/A.groovy").element
    assertEquals("java.util.List<java.lang.Integer>", ref.type.canonicalText)
  }

  void testArrayAccess() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayAccess/A.groovy")
    assertEquals(JAVA_LANG_STRING, ref.type.canonicalText)
  }

  void testReturnTypeByTailExpression() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("returnTypeByTailExpression/A.groovy")
    assertEquals(JAVA_LANG_STRING, ref.type.canonicalText)
  }

  void testParameterWithBuiltinType() {
    GrReferenceExpression refExpr = (GrReferenceExpression)configureByFile("parameterWithBuiltinType/A.groovy")
    assertEquals("int", refExpr.type.canonicalText)
  }

  void testRawTypeInReturnExpression() {
    assertNotNull(resolve("A.groovy"))
  }

  void testMethodCallInvokedOnArrayAccess() {
    final GrReferenceExpression reference = (GrReferenceExpression)configureByFile("A.groovy")
    assertNotNull(reference)
    assertNotNull(reference.type)
    assertEquals("java.lang.Integer", reference.type.canonicalText)
  }

  private void assertTypeEquals(String expected, String fileName) {
    final PsiReference ref = configureByFile(getTestName(true) + "/" + fileName)
    assertInstanceOf(ref, GrReferenceExpression.class)
    final PsiType type = ((GrReferenceExpression)ref).type
    assertNotNull(type)
    assertEquals(expected, type.canonicalText)
  }

  void testTypeOfGroupBy() {
    assertTypeEquals("java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>", "A.groovy")
  }

  void testConditionalExpressionWithNumericTypes() {
    assertTypeEquals("java.lang.Number", "A.groovy")
  }

  void testImplicitCallMethod() {
    assertEquals("java.lang.String", ((GrExpression)configureByFile("A.groovy")).type.canonicalText)
  }

  void testImplicitlyReturnedMethodCall() {
    assertTypeEquals("java.util.Map<BasicRange,java.util.Map<BasicRange,java.lang.Double>>", "A.groovy")
  }

  void testInferWithClosureType() {
    assertTypeEquals("java.util.Date", "A.groovy")
  }

  void testPlusEquals1() {
    assertTypeEquals("Test", "A.groovy")
  }

  void testPlusEquals2() {
    assertTypeEquals("java.lang.String", "A.groovy")
  }

  void testPlusEquals3() {
    assertTypeEquals("java.lang.String", "A.groovy")
  }

  void testPlusEqualsClosure() {
    assertTypeEquals("java.lang.String", "A.groovy")
  }

  void testGetAtClosure() {
    assertTypeEquals("java.lang.String", "A.groovy")
  }

  void testPreferMethodOverloader() {
    assertTypeEquals("java.lang.String", "A.groovy")
  }

  void testSafeInvocationInClassQualifier() {
    final PsiReference ref = configureByFile(getTestName(true) + "/SafeInvocationInClassQualifier.groovy")
    assertInstanceOf(ref, GrReferenceExpression.class)
    assertNull(((GrReferenceExpression)ref).type)
  }

  void testReturnTypeFromMethodClosure() {
    assertTypeEquals("java.lang.String", "A.groovy")
  }

  void testNoSOF() {
    final PsiReference ref = configureByFile(getTestName(true) + "/A.groovy")
    assertInstanceOf(ref, GrReferenceExpression.class)
    final PsiType type = ((GrReferenceExpression)ref).type
    assertTrue(true) //test just should not fail with SOF exception
  }

  void testTraditionalForVar() {
    allowNestedContext(2, testRootDisposable)
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy")
  }

  void testIncMethod() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy")
  }

  void testDGMFind() {
    assertTypeEquals("java.io.File", "a.groovy")
  }

  void testMultiTypeParameter() {
    assertTypeEquals("X | Y", "a.groovy")
  }

  void testSingleParameterInStringInjection() {
    assertTypeEquals("java.io.StringWriter", "a.groovy")
  }

  void testIndexPropertyPlusAssigned() {
    GroovyFile file = myFixture.configureByText('a.groovy', '''
class X {
    def putAt(String s, X x){new Date()}

    def getAt(String s) {new X()}

    def plus(int i) {this}
}

map = new X()

map['i'] += 2
''') as GroovyFile
    GrAssignmentExpression assignment = file.topStatements[2] as GrAssignmentExpression
    assertType("X", assignment.type)
  }

  void testAllTypeParamsAreSubstituted() {
    assertTypeEquals('java.util.Map<java.lang.Object,java.lang.Object>', 'a.groovy')
  }

  void testWildCardsNormalized() {
    assertTypeEquals(Object.canonicalName, 'a.groovy')
  }

  void testIndexPropertyInLHS() {
    assertTypeEquals("java.util.LinkedHashMap<java.lang.Object, java.lang.Object>", 'a.groovy')
  }

  void testRawCollectionsInCasts() {
    doTest('''\
String[] a = ["a"]
def b = a as ArrayList
def cc = b[0]
print c<caret>c''', String.canonicalName)
  }

  void testFind() {
    doTest('''\
def arr =  ['1', '2', '3'] as String[]
def found = arr.find({it=='1'})
print fou<caret>nd''', String.canonicalName)
  }

  void testFindAll() {
    doTest('''\
def arr =  ['1', '2', '3']
def found = arr.findAll({it==1})
print fou<caret>nd''', 'java.util.ArrayList<java.lang.String>')
  }

  void testFindAllForArray() {
    doTest('''\
def arr =  ['1', '2', '3'] as String[]
def found = arr.findAll({it==1})
print fou<caret>nd''', 'java.util.ArrayList<java.lang.String>')
  }

  void testFindAllForSet() {
    myFixture.addClass('''\
package java.util;
class HashSet<T> implements Set<T> {} ''')
    doTest('''\
def arr =  ['1', '2', '3'] as Set<String>
def found = arr.findAll({it==1})
print fou<caret>nd''', 'java.util.HashSet<java.lang.String>')
  }

  void testInferArgumentTypeFromMethod1() {
    allowNestedContextOnce(testRootDisposable)
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    <caret>a.substring(2)
  }
}
''', '[java.lang.Integer,java.lang.String]')
  }

  void testInferArgumentTypeFromMethod2() {
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    <caret>a.substring(2)
}
''', '[java.lang.Integer,java.lang.String]')
  }

  void testInferArgumentTypeFromMethod3() {
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    print a
    <caret>a.substring(2)
}
''', '[java.lang.Integer,java.lang.String]')
  }

  void testInferArgumentTypeFromMethod4() {
    allowNestedContext(2, testRootDisposable)
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    print a
    <caret>a.substring(2)
  }
}
''', '[java.lang.Integer,java.lang.String]')
  }

  void testEmptyListOrListWithGenerics() {
    doTest('''\
def list = cond ? [1, 2, 3] : []
print lis<caret>t
''', "$JAVA_UTIL_LIST<$JAVA_LANG_INTEGER>")
  }

  void testEmptyListOrListWithGenerics2() {
    doTest('''\
def List<Integer> foo(){}
def list = cond ? foo() : []
print lis<caret>t
''', "$JAVA_UTIL_LIST<$JAVA_LANG_INTEGER>")
  }

  void testEmptyMapOrMapWithGenerics() {
    doExprTest '''cond ? [1:'a', 2:'a', 3:'a'] : [:]''', "java.util.LinkedHashMap<java.lang.Integer, java.lang.String>"
  }

  void testEmptyMapOrMapWithGenerics2() {
    doTest('''\
def Map<String, String> foo(){}
def map = cond ? foo() : [:]
print ma<caret>p
''', "$JAVA_UTIL_MAP<$JAVA_LANG_STRING,$JAVA_LANG_STRING>")
  }

  void testSpread1() {
    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
[new A()].stri<caret>ng
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_STRING>")
  }

  void testSpread2() {

    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
class Cat {
  static getFoo(String b) {2}
}
use(Cat) {
  [new A()].string.fo<caret>o
}
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_INTEGER>")
  }

  void testSpread3() {
    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
[[new A()]].stri<caret>ng
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_STRING>>")
  }

  void testSpread4() {
    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
class Cat {
  static getFoo(String b) {2}
}

use(Cat){
  [[new A()]].string.fo<caret>o
}
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_INTEGER>>")
  }

  void testInstanceOfInferring1() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String || oo != null
  o<caret>o
}
''', null)
  }

  void testNegatedInstanceOfInferring1() {
    doTest('''\
def bar(oo) {
  boolean b = oo !instanceof String || oo != null
  o<caret>o
}
''', null)
  }

  void testInstanceOfInferring2() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String || o<caret>o != null
  oo
}
''', null)
  }

  void testNegatedInstanceOfInferring2() {
    doTest('''\
def bar(oo) {
  boolean b = oo !instanceof String || o<caret>o != null
  oo
}
''', JAVA_LANG_STRING)
  }

  void testInstanceOfInferring3() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String && o<caret>o != null
  oo
}
''', String.canonicalName)
  }

  void testNegatedInstanceOfInferring3() {
    doTest('''\
def bar(oo) {
  boolean b = oo !instanceof String && o<caret>o != null
  oo
}
''', null)
  }

  void testInstanceOfInferring4() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String && oo != null
  o<caret>o
}
''', null)
  }

  void testNegatedInstanceOfInferring4() {
    doTest('''\
def bar(oo) {
  boolean b = oo !instanceof String && oo != null
  o<caret>o
}
''', null)
  }

  void testInstanceOfInferring5() {
    doTest('''\
def foo(def oo) {
  if (oo instanceof String && oo instanceof CharSequence) {
    oo
  }
  else {
    o<caret>o
  }

}
''', null)
  }

  void testNegatedInstanceOfInferring5() {
    doTest('''\
def foo(def oo) {
  if (oo !instanceof String || oo !instanceof CharSequence) {
    oo
  }
  else {
    o<caret>o
  }

}
''', JAVA_LANG_STRING)
  }

  void testInstanceOfInferring6() {
    doTest('''\
def foo(bar) {
  if (!(bar instanceof String) && bar instanceof Runnable) {
    ba<caret>r
  }
}''', 'java.lang.Runnable')
  }

  void testNegatedInstanceOfInferring6() {
    doTest('''\
def foo(bar) {
  if (!(bar !instanceof String) || bar !instanceof Runnable) {
    
  } else {
    ba<caret>r
  }
}''', 'java.lang.Runnable')
  }

  void 'test instanceof or instanceof'() {
    doTest '''\
class A {}
class B extends A {}
class C extends A {}
def foo(a) {
    if (a instanceof B || a instanceof C) {
        <caret>a
    }
}
''', 'A'
  }

  void 'test if null typed'() {
    doTest '''\
def foo(String a) {
    if (a == null) {
        <caret>a
    }
}
''', JAVA_LANG_STRING
  }

  void 'test null or instanceof'() {
    doTest '''\
def foo(a) {
    if (a == null || a instanceof String) {
        <caret>a
    }
}
''', JAVA_LANG_STRING
  }

  void 'test null or instanceof 2'() {
    doTest '''\
def foo(a) {
    if (null == a || a instanceof String) {
        <caret>a
    }
}
''', JAVA_LANG_STRING
  }

  void 'test instanceof or null'() {
    doTest '''\
def foo(a) {
    if (a == null || a instanceof String) {
        <caret>a
    }
}
''', JAVA_LANG_STRING
  }

  void 'test instanceof or null 2'() {
    doTest '''\
def foo(a) {
    if (null == a || a instanceof String) {
        <caret>a
    }
}
''', JAVA_LANG_STRING
  }

  void 'test instanceof or null on field'() {
    doTest '''\
class C {
  Object f 
  def foo() {
      if (null == f || f instanceof String) {
          <caret>f
      }
  }
}
''', JAVA_LANG_STRING
  }

  void 'test instanceof or null on field 2'() {
    doTest '''\
class C {
  Object f 
  def foo() {
      if (null == this.f || this.f instanceof String) {
          <caret>f
      }
  }
}
''', JAVA_LANG_STRING
  }

  void 'test null or instanceof else'() {
    doTest '''\
def foo(a) {
    if (a != null && a !instanceof String) {
    
    } else {
        <caret>a
    }
}
''', JAVA_LANG_STRING
  }

  void 'test while null'() {
    doTest '''\
def s = null
while (s == null) {
  s = ""
}
<caret>s
''', JAVA_LANG_STRING
  }

  void 'test while null typed'() {
    doTest '''\
String s = null
while (s == null) {
  s = ""
}
<caret>s
''', JAVA_LANG_STRING
  }

  void 'test enum constant'() {
    doTest('''\
import static MyEnum.*
enum MyEnum {ONE}
if (ONE instanceof String) {
  <caret>ONE
}
''', 'MyEnum')
  }

  void testInString() {
    doTest '''\
def foo(ii) {
  if (ii in String)
    print i<caret>i
}''', 'java.lang.String'
  }

  void testNegatedInString() {
    doTest '''\
def foo(ii) {
  if (ii !in String) {}
  else print i<caret>i
}''', 'java.lang.String'
  }

  void testIndexProperty() {
    allowNestedContextOnce(testRootDisposable)
    doTest('''\
private void getCommonAncestor() {
    def c1 = [new File('a')]
    for (int i = 0; i < 2; i++) {
        if (c1[i] != null) break
        def cur = c1[i]
        print cu<caret>r
    }
}
''', 'java.io.File')
  }

  void testWildcardClosureParam() {
    doTest('''\
class Tx {
    def methodOfT() {}
}

def method(List<? extends Tx> t) {
    t.collect { print i<caret>t }
}
''', 'Tx')
  }

  void testAssert() {
    doTest('''\
def foo(def var) {
  assert var instanceof String
  va<caret>r.isEmpty()
}
''', 'java.lang.String')
  }

  void testUnresolvedSpread() {
    doTest('''\
def xxx = abc*.foo
print xx<caret>x''', 'java.util.List')
  }

  void testThisInCategoryClass() {
    doTest('''\
class Cat {}

@groovy.lang.Category(Cat)
class Any {
  void foo() {
    print th<caret>is
  }
}
''', 'Cat')
  }

  void testNormalizeTypeFromMap() {
    doTest('''\
        def pp = new HashMap<?, ?>().a
        print p<caret>p
''', 'java.lang.Object')
  }

  void testRange() {
    doTest('''\
        def m = new int[3]
        for (ii in 0..<m.length) {
         print i<caret>i
        }
''', 'java.lang.Integer')

    doTest('''\
        def m = new int[3]
        for (ii in m.size()..< m[0]) {
         print i<caret>i
        }
''', 'java.lang.Integer')
  }

  void testUnary() {
    doExprTest('~/abc/', 'java.util.regex.Pattern')
  }

  void testUnary2() {
    doExprTest('-/abc/', null)
  }

  void testUnary3() {
    doExprTest('''
      class A {
        def bitwiseNegate() {'abc'}
      }
      ~new A()
''', 'java.lang.String')
  }

  void testMultiply1() {
    doExprTest('2*2', 'java.lang.Integer')
  }

  void testMultiply2() {
    doExprTest('2f*2f', 'java.lang.Double')
  }

  void testMultiply3() {
    doExprTest('2d*2d', 'java.lang.Double')
  }

  void testMultiply4() {
    doExprTest('2.4*2', 'java.math.BigDecimal')
  }

  void testMultiply5() {
    doExprTest('((byte)2)*((byte)2)', 'java.lang.Integer')
  }

  void testMultiply6() {
    doExprTest('"abc"*"cde"', 'java.lang.String') //expected number as a right operand
  }

  void testMultiply7() {
    doExprTest('''
      class A {
        def multiply(A a) {new B()}
      }
      class B{}
      new A()*new A()
''', 'B')
  }

  void testMultiply8() {
    doExprTest('''
      class A { }
      new A()*new A()
''', null)
  }

  void testDiv1() {
    doExprTest('1/2', 'java.math.BigDecimal')
  }

  void testDiv2() {
    doExprTest('1/2.4', 'java.math.BigDecimal')
  }

  void testDiv3() {
    doExprTest('1d/2', 'java.lang.Double')
  }

  void testDiv4() {
    doExprTest('1f/2', 'java.lang.Double')
  }

  void testDiv5() {
    doExprTest('1f/2.4', 'java.lang.Double')
  }

  void testRecursionWithMaps() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    allowNestedContext(2, testRootDisposable)
    doTest('''
def foo(Map map) {
  while(true)
    ma<caret>p = [a:map]
}
''', 'java.util.LinkedHashMap<java.lang.String, java.util.Map>')
  }

  void testRecursionWithLists() {
    allowNestedContextOnce(testRootDisposable)
    doTest('''
def foo(List list) {
  while(true)
    lis<caret>t = [list]
}
''', 'java.util.List<java.util.List>')
  }

  void testReturnNullWithGeneric() {
    doTest('''
     import groovy.transform.CompileStatic

    @CompileStatic
    class SomeClass {
      protected List foo(String text) {
        return null
      }

      def bar() {
        fo<caret>o("")
      }
    }
  }
''', 'java.util.List')
  }

  void 'test substitutor is not inferred while inferring initializer type'() {
    def file = (GroovyFile)fixture.configureByText('_.groovy', '''\
class A { Closure foo = { 42 } }
''')
    GrClosureType.forbidClosureInference {
      def getter = file.typeDefinitions[0].findMethodsByName("getFoo", false)[0]
      def type = (PsiClassType)PsiUtil.getSmartReturnType(getter)
      assert type.resolve().qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE
    }
  }

  void 'test variable type from null initializer'() {
    doTest 'def v = null; <caret>v', 'null'
  }

  void 'test variable type from null initializer @CompileStatic'() {
    doTest '''\
@groovy.transform.CompileStatic
def foo() {
  def v = null
  <caret>v
}
''', JAVA_LANG_OBJECT
  }

  void testClassExpressions() {
    doExprTest 'String[]', 'java.lang.Class<java.lang.String[]>'
    doExprTest 'Class[]', 'java.lang.Class<java.lang.Class[]>'
    doExprTest 'int[]', 'java.lang.Class<int[]>'
    doExprTest 'float[][]', 'java.lang.Class<float[][]>'
    doExprTest 'Integer[][]', 'java.lang.Class<java.lang.Integer[][]>'
    doExprTest 'boolean[][][]', 'java.lang.Class<boolean[][][]>'

    doExprTest 'String.class', 'java.lang.Class<java.lang.String>'
    doExprTest 'byte.class', 'java.lang.Class<byte>'

    doExprTest 'String[].class', 'java.lang.Class<java.lang.String[]>'
    doExprTest 'Class[].class', 'java.lang.Class<java.lang.Class[]>'
    doExprTest 'int[].class', 'java.lang.Class<int[]>'
    doExprTest 'float[][]', 'java.lang.Class<float[][]>'
    doExprTest 'Integer[][].class', 'java.lang.Class<java.lang.Integer[][]>'
    doExprTest 'double[][][].class', 'java.lang.Class<double[][][]>'
  }

  void testClassExpressionsWithArguments() {
    doExprTest 'String[1]', 'java.lang.Object'
    doExprTest 'String[1][]', 'java.lang.Object'
    doExprTest 'String[1][].class', 'java.lang.Class<? extends java.lang.Object>'
    doExprTest 'int[][1].class', 'java.lang.Class<? extends java.lang.Object>'
  }

  void testClassReference() {
    doExprTest '[].class', "java.lang.Class<? extends java.util.List>"
    doExprTest '1.class', 'java.lang.Class<? extends java.lang.Integer>'
    doExprTest 'String.valueOf(1).class', 'java.lang.Class<? extends java.lang.String>'
    doExprTest '1.getClass()', 'java.lang.Class<? extends java.lang.Integer>'

    doCSExprTest '[].class', "java.lang.Class<? extends java.util.List>"
    doCSExprTest '1.class', 'java.lang.Class<? extends java.lang.Integer>'
    doCSExprTest 'String.valueOf(1).class', 'java.lang.Class<? extends java.lang.String>'
    doCSExprTest '1.getClass()', 'java.lang.Class<? extends java.lang.Integer>'
  }

  void testUnknownClass() {
    doExprTest 'a.class', null
    doCSExprTest 'a.class', 'java.lang.Class<? extends java.lang.Object>'

    doExprTest 'a().class', null
    doCSExprTest 'a().class', 'java.lang.Class'
  }

  void 'test list literal type'() {
    doExprTest '[null]', 'java.util.List'
    doExprTest '["foo", "bar"]', 'java.util.List<java.lang.String>'
    doExprTest '["${foo}", "${bar}"]', 'java.util.List<groovy.lang.GString>'
    doExprTest '[1, "a"]', 'java.util.List<java.io.Serializable>'
  }

  void 'test map literal type'() {
    doExprTest "[a: 'foo']", "java.util.LinkedHashMap<java.lang.String, java.lang.String>"
    doExprTest "[1: 'foo']", "java.util.LinkedHashMap<java.lang.Integer, java.lang.String>"
    doExprTest "[1L: 'foo']", "java.util.LinkedHashMap<java.lang.Long, java.lang.String>"
    doExprTest "[null: 'foo']", "java.util.LinkedHashMap<java.lang.String, java.lang.String>"
    doExprTest "[(null): 'foo']", "java.util.LinkedHashMap<null, java.lang.String>"
    doExprTest "[foo: null]", "java.util.LinkedHashMap<java.lang.String, null>"
    doExprTest "[(null): 'foo', bar: null]", "java.util.LinkedHashMap<java.lang.String, java.lang.String>"
    doExprTest "[foo: 'bar', 2: 'goo']", "java.util.LinkedHashMap<java.io.Serializable, java.lang.String>"
  }

  void 'test recursive literal types'() {
    RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    doExprTest 'def foo() { [foo()] }\nfoo()', "java.util.List<java.util.List>"
    doExprTest 'def foo() { [new Object(), foo()] }\nfoo()', "java.util.List<java.lang.Object>"
    doExprTest 'def foo() { [someKey1: foo()] }\nfoo()', "java.util.LinkedHashMap<java.lang.String, java.util.LinkedHashMap>"
    doExprTest 'def foo() { [someKey0: new Object(), someKey1: foo()] }\nfoo()',
               "java.util.LinkedHashMap<java.lang.String, java.lang.Object>"
  }

  void 'test range literal type'() {
    doExprTest "1..10", "groovy.lang.IntRange"
    doExprTest "'a'..'z'", "groovy.lang.Range<java.lang.String>"
    doExprTest "'b'..1", "groovy.lang.Range<java.io.Serializable>"
  }

  void 'test list with spread'() {
    doExprTest 'def l = [1, 2]; [*l]', 'java.util.List<java.lang.Integer>'
    doExprTest 'def l = [1, 2]; [*[*[*l]]]', 'java.util.List<java.lang.Integer>'
  }

  void 'test map spread dot access'() {
    doExprTest '[foo: 2, bar: 4]*.key', 'java.util.ArrayList<java.lang.String>'
    doExprTest '[foo: 2, bar: 4]*.value', 'java.util.ArrayList<java.lang.Integer>'
    doExprTest '[foo: 2, bar: 4]*.undefined', 'java.util.List'
  }

  void 'test instanceof does not interfere with outer if'() {
    doTest '''\
def bar(CharSequence xx) {
  if (xx instanceof String) {
    1 instanceof Object
    <caret>xx
  }  
}
''', 'java.lang.String'
  }

  void 'test generic tuple inference with type param'() {
    doTest '''\

def <T> T func(T arg){
    return arg
}

def bar() {
    def ll = func([[""]])
    l<caret>l
}
''', 'java.util.List<java.util.List<java.lang.String>>'
  }

  void 'test generic tuple inference with type param 2'() {
    doTest '''\

def <T> T func(T arg){
    return arg
}

def bar() {
    def ll = func([["", 1]])
    l<caret>l
}
''', 'java.util.List<java.util.List<java.io.Serializable>>'
  }

  void 'test enum values() type'() {
    doExprTest 'enum E {}; E.values()', 'E[]'
  }

  void 'test closure owner type'() {
    doTest '''\
class W {
  def c = {
    <caret>owner
  }
}
''', 'W'
  }

  void 'test elvis assignment'() {
    doExprTest 'def a; a ?= "hello"', 'java.lang.String'
    doExprTest 'def a = ""; a ?= null', 'java.lang.String'
    doExprTest 'def a = "s"; a ?= 1', '[java.io.Serializable,java.lang.Comparable<? extends java.io.Serializable>]'
  }

  void 'test spread asImmutable()'() {
    doExprTest('List<List<String>> a; a*.asImmutable()', 'java.util.ArrayList<java.util.List<java.lang.String>>')
  }

  void "test don't start inference for method parameter type"() {
    doTest 'def bar(String ss) { <caret>ss }', 'java.lang.String'
  }

  void 'test assignment in cycle independent on index'() {
    doTest '''\
def foo
for (def i = 1; i < 10; i++) {
  foo = 2
  <caret>foo
}
''', 'java.lang.Integer'
  }

  void 'test assignment in cycle depending on index'() {
    allowNestedContextOnce(testRootDisposable)
    doTest '''\
def foo
for (def i = 1; i < 10; i++) {
  foo = i
  <caret>foo
}
''', 'java.lang.Integer'
  }

  void 'test java field passed as argument to overloaded method'() {
    fixture.addClass '''\
public interface I {}
'''
    fixture.addClass '''\
public class SuperClass {
  public I myField;
}
'''
    doTest '''\
class A extends SuperClass {
  
  static void foo(I s) {}
  static void foo(String s) {}
  
  def usage() {
    foo(myField)
    <caret>myField
  }
}
''', 'I'
  }

  void 'test no soe argument instruction in cycle'() {
    allowNestedContext(3, testRootDisposable)
    doTest '''\
static <U> void foo(U[] a, U[] b) {}

String prevParent = null
while (condition) {
  String parent = null
  foo(parent, prevParent)
  prevParent = parent
}
<caret>prevParent
''', 'java.lang.String'
  }

  void 'test assignment to iterated variable'() {
    doTest '''\
interface I {}
class A implements I {}
class B implements I {
  Iterator<A> iterator() {}
}
def b = new B()
for (a in b) {
  b = a
}
<caret>b
''', '[I,groovy.lang.GroovyObject]'
  }

  void 'test no soe with write to iterated variable in cycle'() {
    allowNestedContext(3, testRootDisposable)
    doTest '''\
while (u) {
  for (a in b) {
    b = a
  }
}
<caret>b
''', null
  }

  void 'test String variable assigned with GString inside closure @CS'() {
    doTest '''\
@groovy.transform.CompileStatic
def test() {
    String key
    return {
        key = "hi ${"there"}"
        <caret>key
    }
}
''', JAVA_LANG_STRING
  }

  void 'test spread list of classes'() {
    doExprTest "[String, Integer]*.'class'", 'java.util.ArrayList<java.lang.Class<? extends java.lang.Class>>'
  }

  void 'test reassigned local CS'() {
    doTest '''
def aa = "1"
a<caret>a.toUpperCase()
if (false) {
    aa = new Object()
    aa
}
aa
''', JAVA_LANG_STRING

    myFixture.getDocument(file).getTextLength()
    def ref = file.findReferenceAt(myFixture.getDocument(file).getTextLength() - 2) as GrReferenceExpression
    def actual = ref.type
    assertType(JAVA_LANG_OBJECT, actual)
  }

  void 'test field with call constraint'() {
    doTest '''
class T {
    def foo(Object o) {}

    def field = ""

    def m() {
        foo(field)
        fie<caret>ld
    }
}
''', JAVA_LANG_STRING
  }

  void 'test reassign field'() {
    doTest '''
class T {
    def field = ""

    def m() {
        field = 1
        fie<caret>ld
    }
}
''', JAVA_LANG_INTEGER
  }

  void 'test field with call constraint CS'() {
    doTest '''
@groovy.transform.CompileStatic
class T {
    def foo(Object o) {}

    def field = ""

    def m() {
        foo(field)
        fie<caret>ld
    }
}
''', JAVA_LANG_OBJECT
  }

  void 'test reassign field CS'() {
    doTest '''
@groovy.transform.CompileStatic
class T {
    def field = ""

    def m() {
        field = 1
        fie<caret>ld
    }
}
''', JAVA_LANG_OBJECT
  }

  void 'test spread call expression in chain call '() {
    doTest '''
[""]*.trim().las<caret>t()
''', JAVA_LANG_STRING
  }

  void 'test spread field expression in chain call '() {
    doTest '''
class C {
  public Integer field
}
[new C()]*.field.las<caret>t()
''', JAVA_LANG_INTEGER
  }
}
