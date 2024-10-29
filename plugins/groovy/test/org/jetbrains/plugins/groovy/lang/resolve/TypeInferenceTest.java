// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import groovy.transform.CompileStatic;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.allowNestedContext;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.allowNestedContextOnce;

@CompileStatic
public class TypeInferenceTest extends TypeInferenceTestBase {

  public void testTryFinallyFlow() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertTrue(type instanceof PsiIntersectionType);
    final PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
    assertEquals(2, conjuncts.length);
  }

  public void testTryFinallyFlow1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow1/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testTryFinallyFlow2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow2/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testThrowVariable() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("throwVariable/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.lang.Exception", type.getCanonicalText());
  }

  public void testGrvy852() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("grvy852/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals(JAVA_LANG_OBJECT, type.getCanonicalText());
  }

  public void testGenericMethod() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericMethod/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.util.List<" + JAVA_LANG_STRING + ">", type.getCanonicalText());
  }

  public void testCircular() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("circular/A.groovy").getElement();
    assertNull(ref.getType());
  }

  public void testCircular1() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("circular1/A.groovy").getElement();
    assertNull(ref.getType());
  }

  public void testCircularMap() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile(getTestName(true) + "/A.groovy").getElement();
    assertNotNull(ref.getType().getInternalCanonicalText());
  }

  public void testClosure() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure/A.groovy").getElement();
    assertNotNull(ref.getType());
  }

  public void testClosure1() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure1/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.Integer"));
  }

  public void testClosure2() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure2/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("int"));
  }

  public void testClosureWithUndefinedValues() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile(getTestName(true) + "/A.groovy").getElement();
    assertNotNull(ref.getType());
  }

  public void testBindingFromInsideClosure() {
    doTest("list = ['a', 'b']; list.each { <caret>it }", JAVA_LANG_STRING);
  }

  public void testGrvy1209() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("grvy1209/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText(JAVA_LANG_STRING));
  }

  public void testLeastUpperBoundClosureType() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("leastUpperBoundClosureType/A.groovy").getElement();
    assertInstanceOf(ref.getType(), PsiImmediateClassType.class);
  }

  public void testJavaLangClassType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("javaLangClassType/A.groovy").getElement();
    assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testGenericWildcard() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericWildcard/A.groovy").getElement();
    assertEquals("X<Base>", ref.getType().getCanonicalText());
  }

  public void testArrayLikeAccessWithIntSequence() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayLikeAccessWithIntSequence/A.groovy").getElement();
    assertEquals("java.util.List<java.lang.Integer>", ref.getType().getCanonicalText());
  }

  public void testArrayAccess() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayAccess/A.groovy");
    assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testReturnTypeByTailExpression() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("returnTypeByTailExpression/A.groovy");
    assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testParameterWithBuiltinType() {
    GrReferenceExpression refExpr = (GrReferenceExpression)configureByFile("parameterWithBuiltinType/A.groovy");
    assertEquals("int", refExpr.getType().getCanonicalText());
  }

  public void testRawTypeInReturnExpression() {
    assertNotNull(resolve("A.groovy"));
  }

  public void testMethodCallInvokedOnArrayAccess() {
    final GrReferenceExpression reference = (GrReferenceExpression)configureByFile("A.groovy");
    assertNotNull(reference);
    assertNotNull(reference.getType());
    assertEquals("java.lang.Integer", reference.getType().getCanonicalText());
  }

  private void assertTypeEquals(String expected, String fileName) {
    final PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    assertInstanceOf(ref, GrReferenceExpression.class);
    final PsiType type = ((GrReferenceExpression)ref).getType();
    assertNotNull(type);
    assertEquals(expected, type.getCanonicalText());
  }

  public void testTypeOfGroupBy() {
    assertTypeEquals("java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>", "A.groovy");
  }

  public void testConditionalExpressionWithNumericTypes() {
    assertTypeEquals("java.lang.Number", "A.groovy");
  }

  public void testImplicitCallMethod() {
    assertEquals(JAVA_LANG_STRING, ((GrExpression)configureByFile("A.groovy")).getType().getCanonicalText());
  }

  public void testImplicitlyReturnedMethodCall() {
    assertTypeEquals("java.util.Map<BasicRange,java.util.Map<BasicRange,java.lang.Double>>", "A.groovy");
  }

  public void testInferWithClosureType() {
    assertTypeEquals("java.util.Date", "A.groovy");
  }

  public void testPlusEquals1() {
    assertTypeEquals("Test", "A.groovy");
  }

  public void testPlusEquals2() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  public void testPlusEquals3() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  public void testPlusEqualsClosure() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  public void testGetAtClosure() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  public void testPreferMethodOverloader() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  public void testSafeInvocationInClassQualifier() {
    final PsiReference ref = configureByFile(getTestName(true) + "/SafeInvocationInClassQualifier.groovy");
    assertInstanceOf(ref, GrReferenceExpression.class);
    assertNull(((GrReferenceExpression)ref).getType());
  }

  public void testReturnTypeFromMethodClosure() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  public void testNoSOF() {
    final PsiReference ref = configureByFile(getTestName(true) + "/A.groovy");
    assertInstanceOf(ref, GrReferenceExpression.class);
    //noinspection unused
    final PsiType type = ((GrReferenceExpression)ref).getType();
    assertTrue(true); // test just should not fail with SOF exception
  }

  public void testTraditionalForVar() {
    allowNestedContext(2, getTestRootDisposable());
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  public void testIncMethod() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  public void testDGMFind() {
    assertTypeEquals("java.io.File", "a.groovy");
  }

  public void testMultiTypeParameter() {
    assertTypeEquals("X | Y", "a.groovy");
  }

  public void testSingleParameterInStringInjection() {
    assertTypeEquals("java.io.StringWriter", "a.groovy");
  }

  public void testIndexPropertyPlusAssigned() {
    //noinspection GrMethodMayBeStatic
    GroovyFile file = (GroovyFile)myFixture.configureByText("a.groovy", """
      class X {
          def putAt(String s, X x){new Date()}
    
          def getAt(String s) {new X()}
    
          def plus(int i) {this}
      }
    
      map = new X()
    
      map['i'] += 2
    """);
    GrAssignmentExpression assignment = (GrAssignmentExpression)file.getTopStatements()[2];
    assertType("java.util.Date", assignment.getType());
  }

  public void testAllTypeParamsAreSubstituted() {
    assertTypeEquals("java.util.Map<" + JAVA_LANG_OBJECT + "," + JAVA_LANG_OBJECT + ">", "a.groovy");
  }

  public void testWildCardsNormalized() {
    assertTypeEquals(JAVA_LANG_OBJECT, "a.groovy");
  }

  public void testIndexPropertyInLHS() {
    assertTypeEquals("java.util.LinkedHashMap<" + JAVA_LANG_OBJECT + ", " + JAVA_LANG_OBJECT + ">", "a.groovy");
  }

  public void testRawCollectionsInCasts() {
    doTest("""
      String[] a = ["a"];
      def b = a as ArrayList;
      def cc = b[0];
      print c<caret>c
      """, JAVA_LANG_STRING);
  }

  public void testFind() {
    doTest("""
      def arr =  ['1', '2', '3'] as String[];
      def found = arr.find({it=='1'});
      print fou<caret>nd
      """, JAVA_LANG_STRING);
  }

  public void testFindAll() {
    doTest("""
      def arr =  ['1', '2', '3'];
      def found = arr.findAll({it==1});
      print fou<caret>nd
      """, "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
  }

  public void testFindAllForArray() {
    doTest("""
      def arr =  ['1', '2', '3'] as String[];
      def found = arr.findAll({it==1});
      print fou<caret>nd
      """, "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
  }

  public void testFindAllForSet() {
    myFixture.addClass("""
      package java.util;
      class HashSet<T> implements Set<T> {}
      """);
    doTest("""
      def arr =  ['1', '2', '3'] as Set<String>;
      def found = arr.findAll({it==1});
      print fou<caret>nd
      """, "java.util.HashSet<" + JAVA_LANG_STRING + ">");
  }

  public void testInferArgumentTypeFromMethod1() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
        while(true) {
          bar(a)
          <caret>a.substring(2)
        }
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  public void testInferArgumentTypeFromMethod2() {
    doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
          bar(a)
          <caret>a.substring(2)
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  public void testInferArgumentTypeFromMethod3() {
    doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
          bar(a)
          print a
          <caret>a.substring(2)
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  public void testInferArgumentTypeFromMethod4() {
    allowNestedContext(2, getTestRootDisposable());
    doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
        while(true) {
          bar(a)
          print a
          <caret>a.substring(2)
        }
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  public void testInferArgumentTypeFromMethod5NoRecursion() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest("""
      void usage(Collection<UnknownClass> x) {
        while (unknownCondition) {
          <caret>foo(x)
        }
      }
      void foo(Collection<UnknownClass> list) {}
    """, "void");
  }

  public void testEmptyListOrListWithGenerics() {
    doTest("""
      def list = cond ? [1, 2, 3] : [];
      print lis<caret>t
      """, JAVA_UTIL_LIST + "<" + JAVA_LANG_INTEGER + ">");
  }

  public void testEmptyListOrListWithGenerics2() {
    doTest("""
      def List<Integer> foo(){}
      def list = cond ? foo() : [];
      print lis<caret>t
      """, JAVA_UTIL_LIST + "<" + JAVA_LANG_INTEGER + ">");
  }

  public void testEmptyMapOrMapWithGenerics() {
    doExprTest("""
      cond ? [1:'a', 2:'a', 3:'a'] : [:]
      """, "java.util.LinkedHashMap<java.lang.Integer, " + JAVA_LANG_STRING + ">");
  }

  public void testEmptyMapOrMapWithGenerics2() {
    doTest("""
      def Map<String, String> foo(){}
      def map = cond ? foo() : [:];
      print ma<caret>p
      """, JAVA_UTIL_MAP + "<" + JAVA_LANG_STRING + "," + JAVA_LANG_STRING + ">");
  }

  public void testSpread1() {
    myFixture.addClass("""
      class A {
        String getString() {return "a";}
      }
      """);
    doTest("""
      [new A()].stri<caret>ng
      """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_STRING + ">");
  }

  public void testSpread2() {
    myFixture.addClass("""
      class A {
        String getString() {return "a";}
      }
      """);
    doTest("""
      class Cat {
        static getFoo(String b) { 2 }
      }
      use(Cat) {
        [new A()].string.fo<caret>o
      }
    """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_INTEGER + ">");
  }

  public void testSpread3() {
      myFixture.addClass("""
        class A {
          String getString() {return "a";}
        }
        """);
    doTest("""
        [[new A()]].stri<caret>ng
      """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_STRING + ">>");
  }

  public void testSpread4() {
    myFixture.addClass("""
      class A {
        String getString() {return "a";}
      }
      """);
    doTest("""
      class Cat {
        static getFoo(String b) { 2 }
      }
    
      use(Cat){
        [[new A()]].string.fo<caret>o
      }
    """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_INTEGER + ">>");
  }

  public void testInstanceOfInferring1() {
    doTest("""
      def bar(oo) {
        boolean b = oo instanceof String || oo != null;
        o<caret>o
      }
      """, null);
  }

  public void testNegatedInstanceOfInferring1() {
    doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String || oo != null;
        o<caret>o
      }
      """, null);
  }

  public void testInstanceOfInferring2() {
    doTest("""
      def bar(oo) {
        boolean b = oo instanceof String || o<caret>o != null;
        oo
      }
      """, null);
  }

  public void testNegatedInstanceOfInferring2() {
    doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String || o<caret>o != null;
        oo
      }
      """, JAVA_LANG_STRING);
  }

  public void testInstanceOfInferring3() {
    doTest("""
      def bar(oo) {
        boolean b = oo instanceof String && o<caret>o != null;
        oo
      }
      """, JAVA_LANG_STRING);
  }

  public void testNegatedInstanceOfInferring3() {
    doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String && o<caret>o != null;
        oo
      }
      """, null);
  }

  public void testInstanceOfInferring4() {
    doTest("""
      def bar(oo) {
        boolean b = oo instanceof String && oo != null;
        o<caret>o
      }
      """, null);
  }

  public void testNegatedInstanceOfInferring4() {
    doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String && oo != null;
        o<caret>o
      }
      """, null);
  }

  public void testInstanceOfInferring5() {
    doTest("""
      def foo(def oo) {
        if (oo instanceof String && oo instanceof CharSequence) {
          oo;
        }
        else {
          o<caret>o
        }
      
      }
      """, null);
  }

  public void testNegatedInstanceOfInferring5() {
    doTest("""
      def foo(def oo) {
        if (oo !instanceof String || oo !instanceof CharSequence) {
          oo;
        }
        else {
          o<caret>o
        }
      
      }
      """, JAVA_LANG_STRING);
  }

  public void testInstanceOfInferring6() {
    doTest("""
      def foo(bar) {
        if (!(bar instanceof String) && bar instanceof Runnable) {
          ba<caret>r
        }
      }
      """, "java.lang.Runnable");
  }

  public void testNegatedInstanceOfInferring6() {
    doTest("""
      def foo(bar) {
        if (!(bar !instanceof String) || bar !instanceof Runnable) {
      
        } else {
          ba<caret>r
        }
      }
      """, "java.lang.Runnable");
  }

  public void testInstanceOfOrInstanceOf() {
    doTest("""
      class A {}
      class B extends A {}
      class C extends A {}
      def foo(a) {
          if (a instanceof B || a instanceof C) {
              <caret>a
          }
      }
      """, "A");
  }

  public void testIfNullTyped() {
    doTest("""
      def foo(String a) {
          if (a == null) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  public void testNullOrInstanceOf() {
    doTest("""
      def foo(a) {
          if (a == null || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  public void testNullOrInstanceOf2() {
    doTest("""
      def foo(a) {
          if (null == a || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  public void testInstanceOfOrNull() {
    doTest("""
      def foo(a) {
          if (a == null || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  public void testInstanceOfOrNull2() {
    doTest("""
      def foo(a) {
          if (null == a || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }
  
  public void testInstanceOfOrNullOnField() {
    doTest(
      """
        class C {
          Object f
          def foo() {
              if (null == f || f instanceof String) {
                  <caret>f
              }
          }
        }
        """, JAVA_LANG_STRING);
  }

  public void testInstanceOfOrNullOnField2() {
    doTest(
      """
        class C {
          Object f
          def foo() {
              if (null == this.f || this.f instanceof String) {
                  <caret>f
              }
          }
        }
        """, JAVA_LANG_STRING);
  }

  public void testNullOrInstanceOfElse() {
    doTest(
      """
        def foo(a) {
            if (a != null && a !instanceof String) {
        
            } else {
                <caret>a
            }
        }
        """, JAVA_LANG_STRING);
  }

  public void testWhileNull() {
    doTest(
      """
        def s = null
        while (s == null) {
          s = ""
        }
        <caret>s
        """, JAVA_LANG_STRING);
  }

  public void testWhileNullTyped() {
    doTest(
      """
        String s = null
        while (s == null) {
          s = ""
        }
        <caret>s
        """, JAVA_LANG_STRING);
  }

  public void testEnumConstant() {
    doTest(
      """
        import static MyEnum.*
        enum MyEnum {ONE}
        if (ONE instanceof String) {
          <caret>ONE
        }
        """, "MyEnum");
  }

  public void testInString() {
    doTest(
      """
        def foo(ii) {
          if (ii in String)
            print i<caret>i
        }
        """, JAVA_LANG_STRING);
  }

  public void testNegatedInString() {
    doTest(
      """
        def foo(ii) {
          if (ii !in String) {}
          else print i<caret>i
        }
        """, JAVA_LANG_STRING);
  }

  public void testIndexProperty() {
    allowNestedContext(2, getTestRootDisposable());
    doTest(
      """
        private void getCommonAncestor() {
            def c1 = [new File('a')]
            for (int i = 0; i < 2; i++) {
                if (c1[i] != null) break
                def cur = c1[i]
                print cu<caret>r
            }
        }
        """, "java.io.File");
  }

  public void testWildcardClosureParam() {
    doTest(
      """
        class Tx {
            def methodOfT() {}
        }
        
        def method(List<? extends Tx> t) {
            t.collect { print i<caret>t }
        }
        """, "Tx");
  }

  public void testAssert() {
    doTest(
      """
        def foo(def var) {
          assert var instanceof String
          va<caret>r.isEmpty()
        }
        """, JAVA_LANG_STRING);
  }

  public void testUnresolvedSpread() {
    doTest(
      """
        def xxx = abc*.foo
        print xx<caret>x""", "java.util.List");
  }

  public void testThisInCategoryClass() {
    doTest(
      """
        class Cat {}
        
        @groovy.lang.Category(Cat)
        class Any {
          void foo() {
            print th<caret>is
          }
        }
        """, "Cat");
  }

  public void testNormalizeTypeFromMap() {
    doTest(
      """
        def pp = new HashMap<?, ?>().a
        print p<caret>p
        """, JAVA_LANG_OBJECT);
  }

  public void testRange() {
    doTest(
      """
        def m = new int[3]
        for (ii in 0..<m.length) {
         print i<caret>i
        }
        """, "java.lang.Integer");

    doTest(
      """
        def m = new int[3]
        for (ii in m.size()..< m[0]) {
         print i<caret>i
        }
        """, "java.lang.Integer");
  }

  public void testUnary() {
    doExprTest("~/abc/", "java.util.regex.Pattern");
  }

  public void testUnary2() {
    doExprTest("-/abc/", null);
  }

  public void testUnary3() {
    doExprTest("""
                 class A {
                   def bitwiseNegate() {'abc'}
                 }
                 ~new A()
                 """, JAVA_LANG_STRING);
  }

  public void testMultiply1() {
    doExprTest("2*2", "java.lang.Integer");
  }

  public void testMultiply2() {
    doExprTest("2f*2f", "java.lang.Double");
  }

  public void testMultiply3() {
    doExprTest("2d*2d", "java.lang.Double");
  }

  public void testMultiply4() {
    doExprTest("2.4*2", "java.math.BigDecimal");
  }

  public void testMultiply5() {
    doExprTest("((byte)2)*((byte)2)", "java.lang.Integer");
  }

  public void testMultiply6() {
    doExprTest("\"abc\"*\"cde\"", JAVA_LANG_STRING); //expected number as a right operand
  }

  public void testMultiply7() {
    doExprTest("""
                 class A {
                   def multiply(A a) {new B()}
                 }
                 class B{}
                 new A()*new A()
                 """, "B");
  }

  public void testMultiply8() {
    doExprTest("""
                 class A { }
                 new A()*new A()
                 """, null);
  }

  public void testDiv1() {
    doExprTest("1/2", "java.math.BigDecimal");
  }

  public void testDiv2() {
    doExprTest("1/2.4", "java.math.BigDecimal");
  }

  public void testDiv3() {
    doExprTest("1d/2", "java.lang.Double");
  }

  public void testDiv4() {
    doExprTest("1f/2", "java.lang.Double");
  }

  public void testDiv5() {
    doExprTest("1f/2.4", "java.lang.Double");
  }

  public void testRecursionWithMaps() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    allowNestedContext(2, getTestRootDisposable());
    doTest(
      """
        def foo(Map map) {
          while(true)
            ma<caret>p = [a:map]
        }
        """, "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", java.util.Map>");
  }

  public void testRecursionWithLists() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest(
      """
        def foo(List list) {
          while(true)
            lis<caret>t = [list]
        }
        """, "java.util.ArrayList<java.util.List>");
  }

  public void testReturnNullWithGeneric() {
    doTest(
      """
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
        """, "java.util.List");
  }

  public void testSubstitutorIsNotInferredWhileInferringInitializerType() {
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy", """
       class A { Closure foo = { 42 } }
      """);

    GrClosureType.forbidClosureInference(() -> {
      PsiMethod getter = file.getTypeDefinitions()[0].findMethodsByName("getFoo", false)[0];
      PsiClassType type = (PsiClassType)PsiUtil.getSmartReturnType(getter);
      assert type.resolve().getQualifiedName().equals(GroovyCommonClassNames.GROOVY_LANG_CLOSURE);
    });
  }

  public void testVariableTypeFromNullInitializer() {
    doTest("def v = null; <caret>v", "null");
  }

  public void testVariableTypeFromNullInitializerCompileStatic() {
    doTest("""
              @groovy.transform.CompileStatic
                 def foo() {
                   def v = null;
                   <caret>v;
                 }
             """, JAVA_LANG_OBJECT);
  }

  public void testClassExpressions() {
    doExprTest("String[]", "java.lang.Class<" + JAVA_LANG_STRING + "[]>");
    doExprTest("Class[]", "java.lang.Class<java.lang.Class[]>");
    doExprTest("int[]", "java.lang.Class<int[]>");
    doExprTest("float[][]", "java.lang.Class<float[][]>");
    doExprTest("Integer[][]", "java.lang.Class<java.lang.Integer[][]>");
    doExprTest("boolean[][][]", "java.lang.Class<boolean[][][]>");

    doExprTest("String.class", "java.lang.Class<" + JAVA_LANG_STRING + ">");
    doExprTest("byte.class", "java.lang.Class<byte>");

    doExprTest("String[].class", "java.lang.Class<" + JAVA_LANG_STRING + "[]>");
    doExprTest("Class[].class", "java.lang.Class<java.lang.Class[]>");
    doExprTest("int[].class", "java.lang.Class<int[]>");
    doExprTest("float[][]", "java.lang.Class<float[][]>");
    doExprTest("Integer[][].class", "java.lang.Class<java.lang.Integer[][]>");
    doExprTest("double[][][].class", "java.lang.Class<double[][][]>");
  }

  public void testClassExpressionsWithArguments() {
    doExprTest("String[1]", JAVA_LANG_OBJECT);
    doExprTest("String[1][]", JAVA_LANG_OBJECT);
    doExprTest("String[1][].class", "java.lang.Class<? extends " + JAVA_LANG_OBJECT + ">");
    doExprTest("int[][1].class", "java.lang.Class<? extends " + JAVA_LANG_OBJECT + ">");
  }

  public void testClassReference() {
    doExprTest("[].class", "java.lang.Class<? extends java.util.ArrayList>");
    doExprTest("1.class", "java.lang.Class<? extends java.lang.Integer>");
    doExprTest("String.valueOf(1).class", "java.lang.Class<? extends " + JAVA_LANG_STRING + ">");
    doExprTest("1.getClass()", "java.lang.Class<? extends java.lang.Integer>");

    doCSExprTest("[].class", "java.lang.Class<? extends java.util.List>");
    doCSExprTest("1.class", "java.lang.Class<? extends java.lang.Integer>");
    doCSExprTest("String.valueOf(1).class", "java.lang.Class<? extends " + JAVA_LANG_STRING + ">");
    doCSExprTest("1.getClass()", "java.lang.Class<? extends java.lang.Integer>");
  }

  public void testUnknownClass() {
    doExprTest("a.class", null);
    doCSExprTest("a.class", "java.lang.Class<? extends " + JAVA_LANG_OBJECT + ">");

    doExprTest("a().class", null);
    doCSExprTest("a().class", "java.lang.Class");
  }

  public void testListLiteralType() {
    doExprTest("[null]", "java.util.ArrayList");
    doExprTest("[\"foo\", \"bar\"]", "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
    doExprTest("[\"${foo}\", \"${bar}\"]", "java.util.ArrayList<groovy.lang.GString>");
    doExprTest("[1, \"a\"]", "java.util.ArrayList<java.io.Serializable>");
  }

  public void testListLiteralTypeCS() {
    doCSExprTest("[null]", "java.util.List");
    doCSExprTest("[\"foo\", \"bar\"]", "java.util.List<" + JAVA_LANG_STRING + ">");
    doCSExprTest("[\"${foo}\", \"${bar}\"]", "java.util.List<groovy.lang.GString>");
    doCSExprTest("[1, \"a\"]", "java.util.List<java.io.Serializable>");
  }

  public void testMapLiteralType() {
    doExprTest("[a: 'foo']", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_STRING + ">");
    doExprTest("[1: 'foo']", "java.util.LinkedHashMap<java.lang.Integer, " + JAVA_LANG_STRING + ">");
    doExprTest("[1L: 'foo']", "java.util.LinkedHashMap<java.lang.Long, " + JAVA_LANG_STRING + ">");
    doExprTest("[null: 'foo']", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_STRING + ">");
    doExprTest("[(null): 'foo']", "java.util.LinkedHashMap<null, " + JAVA_LANG_STRING + ">");
    doExprTest("[foo: null]", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", null>");
    doExprTest("[(null): 'foo', bar: null]", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_STRING + ">");
    doExprTest("[foo: 'bar', 2: 'goo']", "java.util.LinkedHashMap<java.io.Serializable, " + JAVA_LANG_STRING + ">");
  }

  public void testRecursiveLiteralTypes() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    doExprTest(" def foo() { [foo()] }\nfoo()", "java.util.ArrayList<" + JAVA_LANG_OBJECT + ">");
    doExprTest(" def foo() { [new Object(), foo()] }\nfoo()", "java.util.ArrayList<" + JAVA_LANG_OBJECT + ">");
    doExprTest(" def foo() { [someKey1: foo()] }\nfoo()", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", java.util.LinkedHashMap>");
    doExprTest(" def foo() { [someKey0: new Object(), someKey1: foo()] }\nfoo()", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_OBJECT + ">");
  }

  public void testRangeLiteralType() {
    doExprTest("1..10", "groovy.lang.IntRange");
    doExprTest("'a'..'z'", "groovy.lang.Range<" + JAVA_LANG_STRING + ">");
    doExprTest("'b'..1", "groovy.lang.Range<java.io.Serializable>");
  }

  public void testListWithSpread() {
    doExprTest("def l = [1, 2]; [*l]", "java.util.ArrayList<java.lang.Integer>");
    doExprTest("def l = [1, 2]; [*[*[*l]]]", "java.util.ArrayList<java.lang.Integer>");
  }

  public void testMapSpreadDotAccess() {
    doExprTest("[foo: 2, bar: 4]*.key", "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
    doExprTest("[foo: 2, bar: 4]*.value", "java.util.ArrayList<java.lang.Integer>");
    doExprTest("[foo: 2, bar: 4]*.undefined", "java.util.List");
  }

  public void testInstanceofDoesNotInterfereWithOuterIf() {
    doTest("""
              def bar(CharSequence xx) {
                   if (xx instanceof String) {
                         1 instanceof Object
                         <caret>xx
                     }
                 }
             """, JAVA_LANG_STRING);
  }

  public void testGenericTupleInferenceWithTypeParam() {
    doTest("""
              def <T> T func(T arg){
                     return arg
                 }
             
                 def bar() {
                     def ll = func([[""]])
                     l<caret>l
                 }
             """, "java.util.ArrayList<java.util.ArrayList<" + JAVA_LANG_STRING + ">>");
  }

  public void testGenericTupleInferenceWithTypeParam2() {
    doTest("""
              def <T> T func(T arg){
                     return arg
                 }
             
                 def bar() {
                     def ll = func([["", 1]])
                     l<caret>l
                 }
             """, "java.util.ArrayList<java.util.ArrayList<java.io.Serializable>>");
  }

  public void testEnumValuesType() {
    doExprTest("enum E {}; E.values()", "E[]");
  }

  public void testClosureOwnerType() {
    doTest("""
              class W {
                   def c = {
                         <caret>owner
                     }
                 }
             """, "W");
  }

  public void testElvisAssignment() {
    doExprTest("def a; a ?= \"hello\"", JAVA_LANG_STRING);
    doExprTest("def a = \"\"; a ?= null", JAVA_LANG_STRING);
    doExprTest("def a = \"s\"; a ?= 1", "[java.io.Serializable,java.lang.Comparable<? extends java.io.Serializable>]");
  }

  public void testSpreadAsImmutable() {
    doExprTest("List<List<String>> a; a*.asImmutable()", "java.util.ArrayList<java.util.List<" + JAVA_LANG_STRING + ">>");
  }

  public void testDontStartInferenceForMethodParameterType() {
    doTest("def bar(String ss) { <caret>ss }", JAVA_LANG_STRING);
  }

  public void testClosureParam() {
    doTest("""
              interface I { def foo(String s) }
                 def bar(I i) {}
                 bar { var ->
                     <caret>var
                 }
             """, JAVA_LANG_STRING);
  }

  public void testAssignmentInCycleIndependentOnIndex() {
    doTest("""
             def foo
             for (def i = 1; i < 10; i++) {
                 foo = 2
                 <caret>foo
             }
             """, "java.lang.Integer"
    );
  }

  public void testAssignmentInCycleDependingOnIndex() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest("""
             def foo
             for (def i = 1; i < 10; i++) {
                 foo = i
                 <caret>foo
             }
             """, "java.lang.Integer"
    );
  }

  public void testJavaFieldPassedAsArgumentToOverloadedMethod() {
    myFixture.addClass("public interface I {}");
    myFixture.addClass("public class SuperClass { public I myField; }");
    doTest("""
             class A extends SuperClass {
                 static void foo(I s) {}
                 static void foo(String s) {}
                 def usage() {
                     foo(myField)
                     <caret>myField
                 }
             }
             """, "I"
    );
  }

  public void testNoSoeArgumentInstructionInCycle() {
    allowNestedContext(3, getTestRootDisposable());
    doTest("""
             static <U> void foo(U[] a, U[] b) {}
             
             String prevParent = null
             while (condition) {
                 String parent = null
                 foo(parent, prevParent)
                 prevParent = parent
             }
             <caret>prevParent
             """, JAVA_LANG_STRING
    );
  }

  public void testAssignmentToIteratedVariable() {
    doTest("""
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
             """, "[I,groovy.lang.GroovyObject]"
    );
  }

  public void testNoSoeWithWriteToIteratedVariableInCycle() {
    doTest("""
             while (u) {
                 for (a in b) {
                     b = a
                 }
             }
             <caret>b
             """, null
    );
  }

  public void testNoSoeCyclicMultiAssignment() {
    allowNestedContext(4, getTestRootDisposable());
    doTest("""
             def input = ""
             while (condition) {
                 def (name) = parseOption(input)
                 input = input.substring(<caret>name)
             }
             """, null
    );
  }

  public void testStringVariableAssignedWithGStringInsideClosureCS() {
    doTest("""
             @groovy.transform.CompileStatic
             def test() {
                 String key
                 return {
                     key = "hi ${"there"}"
                     <caret>key
                 }
             }
             """, JAVA_LANG_STRING
    );
  }

  public void testSpreadListOfClasses() {
    doExprTest("""
                     [String, Integer]*.'class'
                 """, "java.util.ArrayList<java.lang.Class<? extends java.lang.Class>>");
  }

  public void testReassignedLocalCS() {
    doTest("""
                 def aa = "1"
                 a<caret>a.toUpperCase()
                 if (false) {
                     aa = new Object()
                     aa
                 }
                 aa
             """, JAVA_LANG_STRING);

    var file = getFile();
    //noinspection ResultOfMethodCallIgnored
    myFixture.getDocument(file).getTextLength();
    var ref = (GrReferenceExpression)file.findReferenceAt(myFixture.getDocument(file).getTextLength() - 2);
    var actual = ref.getType();
    assertType(JAVA_LANG_OBJECT, actual);
  }

  public void testFieldWithCallConstraint() {
    doTest("""
                 class T {
                     def foo(Object o) {}
             
                     def field = ""
             
                     def m() {
                         foo(field)
                         fie<caret>ld
                     }
                 }
             """, JAVA_LANG_STRING);
  }

  public void testReassignField() {
    doTest("""
                 class T {
                     def field = ""
             
                     def m() {
                         field = 1
                         fie<caret>ld
                     }
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testFieldWithCallConstraintCS() {
    doTest("""
                 @groovy.transform.CompileStatic
                 class T {
                     def foo(Object o) {}
             
                     def field = ""
             
                     def m() {
                         foo(field)
                         fie<caret>ld
                     }
                 }
             """, JAVA_LANG_OBJECT);
  }

  public void testReassignFieldCS() {
    doTest("""
                 @groovy.transform.CompileStatic
                 class T {
                     def field = ""
             
                     def m() {
                         field = 1
                         fie<caret>ld
                     }
                 }
             """, JAVA_LANG_OBJECT);
  }

  public void testSpreadCallExpressionInChainCall() {
    doTest("""
               [""]*.trim().las<caret>t()
             """, JAVA_LANG_STRING);
  }

  public void testSpreadFieldExpressionInChainCall() {
    doTest("""
                 class C {
                   public Integer field;
                 }
                 [new C()]*.field.las<caret>t()
             """, JAVA_LANG_INTEGER);
  }

  public void testDoTypeInferenceForVariablesFromOuterContext() {
    doTest("""
                 def foo(p) {
                   p = 1
                   def closure = {
                     <caret>p
                   }
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testDoTypeInferenceForOuterVariablesWithExplicitType() {
    doTest("""
                 def foo() {
                   Integer x = 1
                   def closure = {
                     <caret>x
                   }
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testDoTypeInferenceForOuterFinalVariables() {
    doTest("""
                 def foo() {
                   final def x = "string"
                   def closure = { <caret>x }
                 }
             """, JAVA_LANG_STRING);
  }

  public void testDoTypeInferenceForOuterEffectivelyFinalVariables() {
    doTest("""
                 def foo() {
                   def x = "string"
                   def closure = { <caret>x }
                 }
             """, JAVA_LANG_STRING);
  }

  public void testUseOuterContextForClosuresPassedToDGM() {
    doTest("""
                 def foo() {
                     def x = 1
                     'q'.with {
                       <caret>x
                     }
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testUseOuterContextInNestedClosures() {
    doTest("""
                 def foo() {
                     def x = 1
                     'q'.with ({
                       def closure = { <caret>x }
                     })
                     x = ""
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testAllowUseOfOuterContextForNestedDGMClosures() {
    doTest("""
                 def foo(x) {
                   x = 1
                   def cl1 = 1.with { 2.with { <caret>x } }
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testParenthesizedExpression() {
    doTest("""
                 def foo(def p) {
                     def x = 1
                     1.with (({ <caret>x }))
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testAssignmentInsideClosure() {
    doTest("""
                 def foo() {
                   def x = 'q'
                   1.with {
                     x = 1
                   }
                   <caret>x
                 }
             """, "[java.io.Serializable,java.lang.Comparable<? extends java.io.Serializable>]");
  }

  public void testAssignmentInsideClosure2() {
    doTest("""
             class A{}
             class B extends A{}
             class C extends A{}
             def foo() {
               def x = null as C
               [1].each {
                 x = null as B
               }
               <caret>x
             }
             """, "A");
  }

  public void testNoChangesForNullTypeInsideClosure() {
    doTest("""
                 def foo() {
                   def x
                   [1].each {
                       x = 1
                   }
                   <caret>x
                 }
             """, null);
  }

  public void testOtherStatementsInsideClosure() {
    doTest("""
               def method() {
                   def list = []
             
                   [1].each { bar ->
                       bar
                       <caret>list
                 }
               }
             """, "java.util.ArrayList");
  }

  public void testUseDFAResultsFromConditionalBranch() {
    doTest("""
                 def foo(def bar) {
                     if (bar instanceof String) {
                         10.with {
                             <caret>bar
                         }
                     }
                 }
             """, JAVA_LANG_STRING);
  }

  public void testUseMethodCallsInsideClosureBlock() {
    doTest("""
                 def test(def x, y) {
                     y = new A()
                     1.with {
                       y.cast(x)
                       <caret>x
                     }
                 }
             
                 class A {
                     void cast(Integer p) {}
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testUseMethodCallsInsideClosureBlock2() {
    doTest("""
                 def test(def x, y) {
                     y = new A()
                     1.with {
                       2.with {
                         y.cast(x)
                         <caret>x
                       }
                     }
                 }
             
                 class A {
                     void cast(Integer p) {}
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testUseMethodCallsInsideClosureBlock3() {
    doTest("""
                 static def foo(x) {
                     1.with {
                         cast(x)
                         cast(x)
                         <caret>x
                     }
                 }
             
                 static def cast(Integer x) {}
             """, JAVA_LANG_INTEGER);
  }

  public void testDeepNestedInterconnectedVariables() {
    doTest("""
                 static def foo(x, y, z) {
                     1.with {
                         y = new A()
                         2.with {
                             y.foo(z)
                             3.with {
                                 z.cast(x)
                                 4.with {
                                     <caret>x
                                 }
                             }
                         }
                     }
                 }
             
                 class A {
                     def foo(B x) {}
                 }
             
                 class B {
                     def cast(Integer x) {}
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testInstanceofInfluenceOnNestedClosures() {
    doTest("""
                 def test(def x) {
                     1.with {
                         if (x instanceof Integer) {
                             2.with {
                                 <caret>x
                             }
                         }
                     }
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void _testAssignmentInNestedClosure() {
    doTest("""
                 def foo() {
                     def y
                     1.with {
                         2.with {
                             y = 2
                         }
                     }
                     <caret>y
                 }
             """, JAVA_LANG_INTEGER);
  }

  public void testSafeNavigation() {
    doTest("""
                 class A {}
                 class B extends A{}
                 class C extends A{}
             
                 static def foo(x) {
                     def a = new B()
                     1?.with {
                         a = new C()
                     }
                     <caret>a
                 }
             """, "A");
  }

  public void testAssignmentInsideUnknownClosure() {
    doTest("""
                 def foo() {
                   def x = (Number)1
                   def cl = {
                     x = (String)1
                   }
                   <caret>x
                 }
             """, "java.io.Serializable");
  }

  public void _testCSWithSharedVariables() {
    doTest("""
             @groovy.transform.CompileStatic
             def foo() {
               def x = 1
               def cl = {
                 <caret>x
               }
             }
             """, JAVA_LANG_INTEGER);
  }

  public void _testInferLUBForSharedVariables() {
    doTest("""
               class A{}
               class B extends A{}
               class C extends A{}

               @groovy.transform.CompileStatic
               def foo() {
                 def x = new B()
                 x = new C()
                 def cl = {
                   x
                 }
                 <caret>x
               }
             """, "A");
  }

  public void _testFlowTypingShouldNotWorkForSharedVariables() {
    doTest("""
      class A{}
      class B extends A{}
      class C extends A{}
  
      @groovy.transform.CompileStatic
      def foo() {
        def x = new B()
                <caret>x
        def cl = {
          x
        }
        x = new C()
      }
      """, "A");
  }

  public void _testCyclicDependencyForSharedVariables() {
    allowNestedContext(2, getTestRootDisposable());
    doTest("""
      class A{}
      class B extends A{}
      class C extends A{}
  
      @groovy.transform.CompileStatic
      def foo() {
        def x = new B()
        def y = new C()
        x = y
        y = x
            <caret>x
        def cl = {
          x
          y
        }
      }
  
      """, "A");
  }

  public void _testNonSharedVariableDependsOnSharedOne() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest("""
               class A{}
               class B extends A{}
               class C extends A{}

               @groovy.transform.CompileStatic
               def foo() {
                 def x = new B()
                 def z = x
                 <caret>z
                 def cl = { x }
                 x = new C()
               }
             """, "B");
  }

  public void _testAssignmentToSharedVariableInsideClosure() {
    doTest("""
               class A{}
               class B extends A{}
               class C extends A{}

               @groovy.transform.CompileStatic
               def foo() {
                 def x = new B()
                 def cl = { x = new C() }
                 <caret>x
               }
             """, "A");
  }

  public void _testAssignmentToSharedVariableInsideClosureWithAccessFromClosure() {
    doTest("""
               class A{}
               class B extends A{}
               class C extends A{}

               @groovy.transform.CompileStatic
               def foo() {
                 def x = new B()
                 def cl = {
                  x = new C()
                   <caret>x
                 }
               }
             """, "A");
  }

  public void _testDependencyOnSharedVariableWithAssignmentInsideClosure() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest("""
             class A{}
             class B extends A{}
             class C extends A{}
           
             @groovy.transform.CompileStatic
             def foo() {
               def x = new B()
               def cl = { x = new C() }
               def z = x
               <caret>z
             }
           """, "B");
  }

  public void testFlowTypingReachableThroughClosure() {
    doTest("""
             @groovy.transform.CompileStatic
             def foo() {
               def x = 1
               def cl = {
                 def y = x
                 <caret>y
               }
               x = ""
               cl()
             }""", JAVA_LANG_INTEGER);
  }

  public void testAssignmentInsideDanglingClosureDoesNotChangeTypeInParallelFlow() {
    doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             
             def foo() {
               def x = 1
               if (false) {
                 def cl = {
                   x = new B()
                 }
               } else {
                 x = new C()
                 <caret>x
               }
             }""", "C");
  }

  public void testAssignmentInsideDanglingClosureDoesNotChangeTypesBeforeDefinition() {
    doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             
             def foo() {
               def x = 1
               <caret>x
               def cl = {
                 x = new B()
               }
             }""", JAVA_LANG_INTEGER);
  }

  public void _testTwoDanglingClosuresFlushTypeTogether() {
    doTest("""
             class A {}
             class D extends A{}
             class B extends D {}
             class C extends D {}
             
             def foo() {
               def x = new B()
               def cl = {
                 x = new A()
               }
               def cl2 = {
                 x = new C()
               }
               <caret>x
             }""", "A");
  }

  public void _testTwoAssignmentsInsideSingleDanglingClosure() {
    doTest("""
             class A {}
             class D extends A{}
             class B extends D {}
             class C extends D {}
             
             def foo() {
               def x = 1
               def cl = {
                 x = new A()
                 x = new C()
               }
               x = new B()
               <caret>x
             }""", "A");
  }

  public void testAssignmentInNestedDanglingClosure2() {
    doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             
             def foo() {
               def x = new B()
               def cl = {
                 def cl = {
                   x = new C()
                 }
               }
               <caret>x
             }""", "A");
  }

  public void testShadowedField() {
    doTest("""
             class A {
             
                 def counter = 200
             
                 def foo() {
                     1.with {
                         counter = "s"
                         def counter = new ArrayList<Integer>()
                     }
                     <caret>counter
                 }
             }""", JAVA_LANG_INTEGER);
  }

  public void testCyclicFlowWithClosure() {
    allowNestedContext(2, getTestRootDisposable());
    doTest("""
             def x
             for (def i = 0; i < 10; i++) {
               1.with {
                 x = i
                 i++
                 <caret>x
               }
             }
             """, JAVA_LANG_INTEGER);
  }

  public void testCycleWithUnknownClosure() {
    //allowNestedContext(2, getTestRootDisposable())
    doTest("""
             static  bar(Closure cl) {}
             
             static def foo() {
                 for (def i = 0; i < 10; i = bar { i++ }) {
                   <caret>i
                 }
             }
             """, JAVA_LANG_INTEGER);
  }

  public void _testNoSOEInOperatorUsageWithSharedVariable() {
    allowNestedContextOnce(getTestRootDisposable());
    doTest("""
             @groovy.transform.CompileStatic
             private void checkResult(String expected) {
               def offset = 0
               actualParameterHints.each { it ->
                offset += hintString
               }
               <caret>offset
             }
             """, JAVA_LANG_INTEGER);
  }

  public void testCacheConsistencyForClosuresInCycle() {
    doTest("""
             private void foo(String expected) {
               def b = [1, 2, 3]
               for (a in b) {
                 1.with {
                   b = a
                 }
               }
               <caret>b
             }
             """, JAVA_IO_SERIALIZABLE);
  }

  public void testCacheConsistencyForClosuresInCycle2() {
    doTest("""
             interface J {}
             interface R extends J {}
             interface I extends J {}
             class A implements I {}
             class B implements I {
               Iterator<R> iterator() {}
             }
             
             def foo() {
               def b = new B()
               for (a in b) {
                 1.with {
                   if (true) {
                     b = new A()
                   }
                 }
                 <caret>b
                 b = a
               }
             }
             """, "J");
  }

  public void testInitialTypeInfluencesDFA() {
    doTest("""
             interface I {}
             class A implements I {}
             class B implements I {}
             
             class C {
               def x = new B()
             
               def foo() {
                 if (true) {
                   x = new A()
                 }
                 <caret>x
               }
             }
             """, "[I,groovy.lang.GroovyObject]");
  }

  public void testMixinWithUnknownIdentifier() {
    doTest("""
             protected void onLoadConfig (Map configSection) {
                 if (configSection.presetMode != null)
                   setPresetMode(p<caret>resetMode)
             }""", null);
  }

  public void testSOEWithLargeFlow() {
    RecursionManager.disableAssertOnRecursionPrevention(getTestRootDisposable());
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    allowNestedContext(5, getTestRootDisposable());
    doTest("""
             static _getTreeData() {
                 def filterData  = []
                 for (EcmQueryMaskStructureNode node: childNodes) {
                     def currentNodeFilter = null
                     filterData.e<caret>ach { filter ->
                         currentNodeFilter = filter
                     };
                     nodePerformance["getChildValues"] = prepareChildNodes(filterData, currentNodeFilter)
                 }
             }
             """, "java.util.ArrayList");
  }

  public void testThrowInClosure() {
    doTest("""
             static String rootDir(Path archive) {
                 return createTarGzInputStream(archive).withCloseable {
                     <caret>it.nextTarEntry?.name ?: { throw new IllegalStateException("Unable to read $archive") }()
                 }
             }
             """, null);
  }

  public void testFlowTypingWithDGM() {
    doTest("""
             def <T> Collection<T> aa(Closure<? extends Collection<? extends T>> cl) {
             }
             
             def foo() {
                 def xx = aa { [1] }
                 x<caret>x
             }
             """, "java.util.Collection<java.lang.Integer>");
  }

  public void testBoxingOnNullableReceiver() {
    doTest("""
             def xx = ""?.length()
             x<caret>x
             """, "java.lang.Integer");
  }
}
