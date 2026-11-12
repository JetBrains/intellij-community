// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import groovy.transform.CompileStatic;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.groovy.testFramework.GroovyAssertions.assertType;
import static com.intellij.psi.CommonClassNames.JAVA_IO_SERIALIZABLE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_ARRAY_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.allowNestedContext;
import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt.allowNestedContextOnce;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CompileStatic
@TestApplication
public class TypeInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @Test
  public void testTryFinallyFlow() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("tryFinallyFlow/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertTrue(type instanceof PsiIntersectionType);
      final PsiType[] conjuncts = ((PsiIntersectionType)type).getConjuncts();
      assertEquals(2, conjuncts.length);
    });
  }

  @Test
  public void testTryFinallyFlow1() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("tryFinallyFlow1/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertNotNull(type);
      assertTrue(type.equalsToText("java.lang.Integer"));
    });
  }

  @Test
  public void testTryFinallyFlow2() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("tryFinallyFlow2/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertNotNull(type);
      assertTrue(type.equalsToText("java.lang.Integer"));
    });
  }

  @Test
  public void testThrowVariable() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("throwVariable/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertNotNull(type);
      assertEquals("java.lang.Exception", type.getCanonicalText());
    });
  }

  @Test
  public void testGrvy852() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("grvy852/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertNotNull(type);
      assertEquals(JAVA_LANG_OBJECT, type.getCanonicalText());
    });
  }

  @Test
  public void testGenericMethod() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("genericMethod/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertNotNull(type);
      assertEquals("java.util.List<" + JAVA_LANG_STRING + ">", type.getCanonicalText());
    });
  }

  @Test
  public void testCircular() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("circular/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertNull(ref.getType());
    });
  }

  @Test
  public void testCircular1() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("circular1/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertNull(ref.getType());
    });
  }

  @Test
  public void testCircularMap() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile(myFixture.getTestName(true) + "/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.getType().getInternalCanonicalText());
    });
  }

  @Test
  public void testClosure() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("closure/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.getType());
    });
  }

  @Test
  public void testClosure1() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("closure1/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertTrue(ref.getType().equalsToText("java.lang.Integer"));
    });
  }

  @Test
  public void testClosure2() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("closure2/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertTrue(ref.getType().equalsToText("int"));
    });
  }

  @Test
  public void testClosureWithUndefinedValues() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile(myFixture.getTestName(true) + "/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.getType());
    });
  }

  @Test
  public void testBindingFromInsideClosure() {
    inference.get().doTest("list = ['a', 'b']; list.each { <caret>it }", JAVA_LANG_STRING);
  }

  @Test
  public void testGrvy1209() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("grvy1209/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertTrue(ref.getType().equalsToText(JAVA_LANG_STRING));
    });
  }

  @Test
  public void testLeastUpperBoundClosureType() {
    GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("leastUpperBoundClosureType/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiImmediateClassType.class, ref.getType());
    });
  }

  @Test
  public void testJavaLangClassType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("javaLangClassType/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testGenericWildcard() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("genericWildcard/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertEquals("X<Base>", ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testArrayLikeAccessWithIntSequence() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("arrayLikeAccessWithIntSequence/A.groovy").getElement();
    ReadAction.runBlocking(() -> {
      assertEquals("java.util.List<java.lang.Integer>", ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testArrayAccess() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("arrayAccess/A.groovy");
    ReadAction.runBlocking(() -> {
      assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testReturnTypeByTailExpression() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByFile("returnTypeByTailExpression/A.groovy");
    ReadAction.runBlocking(() -> {
      assertEquals(JAVA_LANG_STRING, ref.getType().getCanonicalText());
    });
  }

  @Test
  public void testParameterWithBuiltinType() {
    GrReferenceExpression refExpr = (GrReferenceExpression)inference.get().configureByFile("parameterWithBuiltinType/A.groovy");
    ReadAction.runBlocking(() -> {
      assertEquals("int", refExpr.getType().getCanonicalText());
    });
  }

  @Test
  public void testRawTypeInReturnExpression() {
    assertNotNull(inference.get().resolve("A.groovy"));
  }

  @Test
  public void testMethodCallInvokedOnArrayAccess() {
    final GrReferenceExpression reference = (GrReferenceExpression)inference.get().configureByFile("A.groovy");
    ReadAction.runBlocking(() -> {
      assertNotNull(reference);
      assertNotNull(reference.getType());
      assertEquals("java.lang.Integer", reference.getType().getCanonicalText());
    });
  }

  private void assertTypeEquals(String expected, String fileName) {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    final PsiReference ref = inference.get().configureByFile(myFixture.getTestName(true) + "/" + fileName);
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrReferenceExpression.class, ref);
      final PsiType type = ((GrReferenceExpression)ref).getType();
      assertNotNull(type);
      assertEquals(expected, type.getCanonicalText());
    });
  }

  @Test
  public void testTypeOfGroupBy() {
    assertTypeEquals("java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>", "A.groovy");
  }

  @Test
  public void testConditionalExpressionWithNumericTypes() {
    assertTypeEquals("java.lang.Number", "A.groovy");
  }

  @Test
  public void testImplicitCallMethod() {
    GrExpression expression = (GrExpression)inference.get().configureByFile("A.groovy");
    assertEquals(JAVA_LANG_STRING, ReadAction.computeBlocking(() -> expression.getType().getCanonicalText()));
  }

  @Test
  public void testImplicitlyReturnedMethodCall() {
    assertTypeEquals("java.util.Map<BasicRange,java.util.Map<BasicRange,java.lang.Double>>", "A.groovy");
  }

  @Test
  public void testInferWithClosureType() {
    assertTypeEquals("java.util.Date", "A.groovy");
  }

  @Test
  public void testPlusEquals1() {
    assertTypeEquals("Test", "A.groovy");
  }

  @Test
  public void testPlusEquals2() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  @Test
  public void testPlusEquals3() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  @Test
  public void testPlusEqualsClosure() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  @Test
  public void testGetAtClosure() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  @Test
  public void testPreferMethodOverloader() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  @Test
  public void testSafeInvocationInClassQualifier() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    final PsiReference ref = inference.get().configureByFile(myFixture.getTestName(true) + "/SafeInvocationInClassQualifier.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrReferenceExpression.class, ref);
      assertNull(((GrReferenceExpression)ref).getType());
    });
  }

  @Test
  public void testReturnTypeFromMethodClosure() {
    assertTypeEquals(JAVA_LANG_STRING, "A.groovy");
  }

  @Test
  public void testNoSOF() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    final PsiReference ref = inference.get().configureByFile(myFixture.getTestName(true) + "/A.groovy");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrReferenceExpression.class, ref);
      //noinspection unused
      final PsiType type = ((GrReferenceExpression)ref).getType();
      assertTrue(true); // test just should not fail with SOF exception
    });
  }

  @Test
  public void testTraditionalForVar() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(2, myFixture.getTestRootDisposable());
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  @Test
  public void testIncMethod() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  @Test
  public void testDGMFind() {
    assertTypeEquals("java.io.File", "a.groovy");
  }

  @Test
  public void testMultiTypeParameter() {
    assertTypeEquals("X | Y", "a.groovy");
  }

  @Test
  public void testSingleParameterInStringInjection() {
    assertTypeEquals("java.io.StringWriter", "a.groovy");
  }

  @Test
  public void testIndexPropertyPlusAssigned() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
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
    ReadAction.runBlocking(() -> {
      GrAssignmentExpression assignment = (GrAssignmentExpression)file.getTopStatements()[2];
      assertType("java.util.Date", assignment.getType());
    });
  }

  @Test
  public void testAllTypeParamsAreSubstituted() {
    assertTypeEquals("java.util.Map<" + JAVA_LANG_OBJECT + "," + JAVA_LANG_OBJECT + ">", "a.groovy");
  }

  @Test
  public void testWildCardsNormalized() {
    assertTypeEquals(JAVA_LANG_OBJECT, "a.groovy");
  }

  @Test
  public void testIndexPropertyInLHS() {
    assertTypeEquals("java.util.LinkedHashMap<" + JAVA_LANG_OBJECT + ", " + JAVA_LANG_OBJECT + ">", "a.groovy");
  }

  @Test
  public void testRawCollectionsInCasts() {
    inference.get().doTest("""
      String[] a = ["a"];
      def b = a as ArrayList;
      def cc = b[0];
      print c<caret>c
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testFind() {
    inference.get().doTest("""
      def arr =  ['1', '2', '3'] as String[];
      def found = arr.find({it=='1'});
      print fou<caret>nd
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testFindAll() {
    inference.get().doTest("""
      def arr =  ['1', '2', '3'];
      def found = arr.findAll({it==1});
      print fou<caret>nd
      """, "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testFindAllForArray() {
    inference.get().doTest("""
      def arr =  ['1', '2', '3'] as String[];
      def found = arr.findAll({it==1});
      print fou<caret>nd
      """, "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testFindAllForSet() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    myFixture.addClass("""
      package java.util;
      class HashSet<T> implements Set<T> {}
      """);
    inference.get().doTest("""
      def arr =  ['1', '2', '3'] as Set<String>;
      def found = arr.findAll({it==1});
      print fou<caret>nd
      """, "java.util.HashSet<" + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testInferArgumentTypeFromMethod1() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
        while(true) {
          bar(a)
          <caret>a.substring(2)
        }
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  @Test
  public void testInferArgumentTypeFromMethod2() {
    inference.get().doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
          bar(a)
          <caret>a.substring(2)
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  @Test
  public void testInferArgumentTypeFromMethod3() {
    inference.get().doTest("""
      def bar(String s) {}
      
      def foo(Integer a) {
          bar(a)
          print a
          <caret>a.substring(2)
      }
      """, "[java.lang.Integer," + JAVA_LANG_STRING + "]");
  }

  @Test
  public void testInferArgumentTypeFromMethod4() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(2, myFixture.getTestRootDisposable());
    inference.get().doTest("""
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

  @Test
  public void testInferArgumentTypeFromMethod5NoRecursion() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest("""
      void usage(Collection<UnknownClass> x) {
        while (unknownCondition) {
          <caret>foo(x)
        }
      }
      void foo(Collection<UnknownClass> list) {}
    """, "void");
  }

  @Test
  public void testEmptyListOrListWithGenerics() {
    inference.get().doTest("""
      def list = cond ? [1, 2, 3] : [];
      print lis<caret>t
      """, JAVA_UTIL_LIST + "<" + JAVA_LANG_INTEGER + ">");
  }

  @Test
  public void testEmptyListOrListWithGenerics2() {
    inference.get().doTest("""
      def List<Integer> foo(){}
      def list = cond ? foo() : [];
      print lis<caret>t
      """, JAVA_UTIL_LIST + "<" + JAVA_LANG_INTEGER + ">");
  }

  @Test
  public void testEmptyMapOrMapWithGenerics() {
    inference.get().doExprTest("""
      cond ? [1:'a', 2:'a', 3:'a'] : [:]
      """, "java.util.LinkedHashMap<java.lang.Integer, " + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testEmptyMapOrMapWithGenerics2() {
    inference.get().doTest("""
      def Map<String, String> foo(){}
      def map = cond ? foo() : [:];
      print ma<caret>p
      """, JAVA_UTIL_MAP + "<" + JAVA_LANG_STRING + "," + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testSpread1() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    myFixture.addClass("""
      class A {
        String getString() {return "a";}
      }
      """);
    inference.get().doTest("""
      [new A()].stri<caret>ng
      """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testSpread2() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    myFixture.addClass("""
      class A {
        String getString() {return "a";}
      }
      """);
    inference.get().doTest("""
      class Cat {
        static getFoo(String b) { 2 }
      }
      use(Cat) {
        [new A()].string.fo<caret>o
      }
    """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_INTEGER + ">");
  }

  @Test
  public void testSpread3() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
      myFixture.addClass("""
        class A {
          String getString() {return "a";}
        }
        """);
    inference.get().doTest("""
        [[new A()]].stri<caret>ng
      """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_STRING + ">>");
  }

  @Test
  public void testSpread4() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    myFixture.addClass("""
      class A {
        String getString() {return "a";}
      }
      """);
    inference.get().doTest("""
      class Cat {
        static getFoo(String b) { 2 }
      }
    
      use(Cat){
        [[new A()]].string.fo<caret>o
      }
    """, JAVA_UTIL_ARRAY_LIST + "<" + JAVA_UTIL_ARRAY_LIST + "<" + JAVA_LANG_INTEGER + ">>");
  }

  @Test
  public void testInstanceOfInferring1() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo instanceof String || oo != null;
        o<caret>o
      }
      """, null);
  }

  @Test
  public void testNegatedInstanceOfInferring1() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String || oo != null;
        o<caret>o
      }
      """, null);
  }

  @Test
  public void testInstanceOfInferring2() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo instanceof String || o<caret>o != null;
        oo
      }
      """, null);
  }

  @Test
  public void testNegatedInstanceOfInferring2() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String || o<caret>o != null;
        oo
      }
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testInstanceOfInferring3() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo instanceof String && o<caret>o != null;
        oo
      }
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testNegatedInstanceOfInferring3() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String && o<caret>o != null;
        oo
      }
      """, null);
  }

  @Test
  public void testInstanceOfInferring4() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo instanceof String && oo != null;
        o<caret>o
      }
      """, null);
  }

  @Test
  public void testNegatedInstanceOfInferring4() {
    inference.get().doTest("""
      def bar(oo) {
        boolean b = oo !instanceof String && oo != null;
        o<caret>o
      }
      """, null);
  }

  @Test
  public void testInstanceOfInferring5() {
    inference.get().doTest("""
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

  @Test
  public void testNegatedInstanceOfInferring5() {
    inference.get().doTest("""
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

  @Test
  public void testInstanceOfInferring6() {
    inference.get().doTest("""
      def foo(bar) {
        if (!(bar instanceof String) && bar instanceof Runnable) {
          ba<caret>r
        }
      }
      """, "java.lang.Runnable");
  }

  @Test
  public void testNegatedInstanceOfInferring6() {
    inference.get().doTest("""
      def foo(bar) {
        if (!(bar !instanceof String) || bar !instanceof Runnable) {
      
        } else {
          ba<caret>r
        }
      }
      """, "java.lang.Runnable");
  }

  @Test
  public void testInstanceOfOrInstanceOf() {
    inference.get().doTest("""
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

  @Test
  public void testIfNullTyped() {
    inference.get().doTest("""
      def foo(String a) {
          if (a == null) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testNullOrInstanceOf() {
    inference.get().doTest("""
      def foo(a) {
          if (a == null || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testNullOrInstanceOf2() {
    inference.get().doTest("""
      def foo(a) {
          if (null == a || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testInstanceOfOrNull() {
    inference.get().doTest("""
      def foo(a) {
          if (a == null || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }

  @Test
  public void testInstanceOfOrNull2() {
    inference.get().doTest("""
      def foo(a) {
          if (null == a || a instanceof String) {
              <caret>a
          }
      }
      """, JAVA_LANG_STRING);
  }
  
  @Test
  public void testInstanceOfOrNullOnField() {
    inference.get().doTest(
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

  @Test
  public void testInstanceOfOrNullOnField2() {
    inference.get().doTest(
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

  @Test
  public void testNullOrInstanceOfElse() {
    inference.get().doTest(
      """
        def foo(a) {
            if (a != null && a !instanceof String) {
        
            } else {
                <caret>a
            }
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testWhileNull() {
    inference.get().doTest(
      """
        def s = null
        while (s == null) {
          s = ""
        }
        <caret>s
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testWhileNullTyped() {
    inference.get().doTest(
      """
        String s = null
        while (s == null) {
          s = ""
        }
        <caret>s
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testEnumConstant() {
    inference.get().doTest(
      """
        import static MyEnum.*
        enum MyEnum {ONE}
        if (ONE instanceof String) {
          <caret>ONE
        }
        """, "MyEnum");
  }

  @Test
  public void testInString() {
    inference.get().doTest(
      """
        def foo(ii) {
          if (ii in String)
            print i<caret>i
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testNegatedInString() {
    inference.get().doTest(
      """
        def foo(ii) {
          if (ii !in String) {}
          else print i<caret>i
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testIndexProperty() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(2, myFixture.getTestRootDisposable());
    inference.get().doTest(
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

  @Test
  public void testWildcardClosureParam() {
    inference.get().doTest(
      """
        class Tx {
            def methodOfT() {}
        }
        
        def method(List<? extends Tx> t) {
            t.collect { print i<caret>t }
        }
        """, "Tx");
  }

  @Test
  public void testAssert() {
    inference.get().doTest(
      """
        def foo(def var) {
          assert var instanceof String
          va<caret>r.isEmpty()
        }
        """, JAVA_LANG_STRING);
  }

  @Test
  public void testUnresolvedSpread() {
    inference.get().doTest(
      """
        def xxx = abc*.foo
        print xx<caret>x""", "java.util.List");
  }

  @Test
  public void testThisInCategoryClass() {
    inference.get().doTest(
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

  @Test
  public void testNormalizeTypeFromMap() {
    inference.get().doTest(
      """
        def pp = new HashMap<?, ?>().a
        print p<caret>p
        """, JAVA_LANG_OBJECT);
  }

  @Test
  public void testRange() {
    inference.get().doTest(
      """
        def m = new int[3]
        for (ii in 0..<m.length) {
         print i<caret>i
        }
        """, "java.lang.Integer");

    inference.get().doTest(
      """
        def m = new int[3]
        for (ii in m.size()..< m[0]) {
         print i<caret>i
        }
        """, "java.lang.Integer");
  }

  @Test
  public void testUnary() {
    inference.get().doExprTest("~/abc/", "java.util.regex.Pattern");
  }

  @Test
  public void testUnary2() {
    inference.get().doExprTest("-/abc/", null);
  }

  @Test
  public void testUnary3() {
    inference.get().doExprTest("""
                 class A {
                   def bitwiseNegate() {'abc'}
                 }
                 ~new A()
                 """, JAVA_LANG_STRING);
  }

  @Test
  public void testMultiply1() {
    inference.get().doExprTest("2*2", "java.lang.Integer");
  }

  @Test
  public void testMultiply2() {
    inference.get().doExprTest("2f*2f", "java.lang.Double");
  }

  @Test
  public void testMultiply3() {
    inference.get().doExprTest("2d*2d", "java.lang.Double");
  }

  @Test
  public void testMultiply4() {
    inference.get().doExprTest("2.4*2", "java.math.BigDecimal");
  }

  @Test
  public void testMultiply5() {
    inference.get().doExprTest("((byte)2)*((byte)2)", "java.lang.Integer");
  }

  @Test
  public void testMultiply6() {
    inference.get().doExprTest("\"abc\"*\"cde\"", JAVA_LANG_STRING); //expected number as a right operand
  }

  @Test
  public void testMultiply7() {
    inference.get().doExprTest("""
                 class A {
                   def multiply(A a) {new B()}
                 }
                 class B{}
                 new A()*new A()
                 """, "B");
  }

  @Test
  public void testMultiply8() {
    inference.get().doExprTest("""
                 class A { }
                 new A()*new A()
                 """, null);
  }

  @Test
  public void testDiv1() {
    inference.get().doExprTest("1/2", "java.math.BigDecimal");
  }

  @Test
  public void testDiv2() {
    inference.get().doExprTest("1/2.4", "java.math.BigDecimal");
  }

  @Test
  public void testDiv3() {
    inference.get().doExprTest("1d/2", "java.lang.Double");
  }

  @Test
  public void testDiv4() {
    inference.get().doExprTest("1f/2", "java.lang.Double");
  }

  @Test
  public void testDiv5() {
    inference.get().doExprTest("1f/2.4", "java.lang.Double");
  }

  @Test
  public void testRecursionWithMaps() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    allowNestedContext(2, myFixture.getTestRootDisposable());
    inference.get().doTest(
      """
        def foo(Map map) {
          while(true)
            ma<caret>p = [a:map]
        }
        """, "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", java.util.Map>");
  }

  @Test
  public void testRecursionWithLists() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest(
      """
        def foo(List list) {
          while(true)
            lis<caret>t = [list]
        }
        """, "java.util.ArrayList<java.util.List>");
  }

  @Test
  public void testReturnNullWithGeneric() {
    inference.get().doTest(
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

  @Test
  public void testSubstitutorIsNotInferredWhileInferringInitializerType() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy", """
       class A { Closure foo = { 42 } }
      """);

    ReadAction.runBlocking(() -> {
      GrClosureType.forbidClosureInference(() -> {
        PsiMethod getter = file.getTypeDefinitions()[0].findMethodsByName("getFoo", false)[0];
        PsiClassType type = (PsiClassType)PsiUtil.getSmartReturnType(getter);
        assertEquals(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, type.resolve().getQualifiedName());
      });
    });
  }

  @Test
  public void testVariableTypeFromNullInitializer() {
    inference.get().doTest("def v = null; <caret>v", "null");
  }

  @Test
  public void testVariableTypeFromNullInitializerCompileStatic() {
    inference.get().doTest("""
              @groovy.transform.CompileStatic
                 def foo() {
                   def v = null;
                   <caret>v;
                 }
             """, JAVA_LANG_OBJECT);
  }

  @Test
  public void testClassExpressions() {
    inference.get().doExprTest("String[]", "java.lang.Class<" + JAVA_LANG_STRING + "[]>");
    inference.get().doExprTest("Class[]", "java.lang.Class<java.lang.Class[]>");
    inference.get().doExprTest("int[]", "java.lang.Class<int[]>");
    inference.get().doExprTest("float[][]", "java.lang.Class<float[][]>");
    inference.get().doExprTest("Integer[][]", "java.lang.Class<java.lang.Integer[][]>");
    inference.get().doExprTest("boolean[][][]", "java.lang.Class<boolean[][][]>");

    inference.get().doExprTest("String.class", "java.lang.Class<" + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("byte.class", "java.lang.Class<byte>");

    inference.get().doExprTest("String[].class", "java.lang.Class<" + JAVA_LANG_STRING + "[]>");
    inference.get().doExprTest("Class[].class", "java.lang.Class<java.lang.Class[]>");
    inference.get().doExprTest("int[].class", "java.lang.Class<int[]>");
    inference.get().doExprTest("float[][]", "java.lang.Class<float[][]>");
    inference.get().doExprTest("Integer[][].class", "java.lang.Class<java.lang.Integer[][]>");
    inference.get().doExprTest("double[][][].class", "java.lang.Class<double[][][]>");
  }

  @Test
  public void testClassExpressionsWithArguments() {
    inference.get().doExprTest("String[1]", JAVA_LANG_OBJECT);
    inference.get().doExprTest("String[1][]", JAVA_LANG_OBJECT);
    inference.get().doExprTest("String[1][].class", "java.lang.Class<? extends " + JAVA_LANG_OBJECT + ">");
    inference.get().doExprTest("int[][1].class", "java.lang.Class<? extends " + JAVA_LANG_OBJECT + ">");
  }

  @Test
  public void testClassReference() {
    inference.get().doExprTest("[].class", "java.lang.Class<? extends java.util.ArrayList>");
    inference.get().doExprTest("1.class", "java.lang.Class<? extends java.lang.Integer>");
    inference.get().doExprTest("String.valueOf(1).class", "java.lang.Class<? extends " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("1.getClass()", "java.lang.Class<? extends java.lang.Integer>");

    inference.get().doCSExprTest("[].class", "java.lang.Class<? extends java.util.List>");
    inference.get().doCSExprTest("1.class", "java.lang.Class<? extends java.lang.Integer>");
    inference.get().doCSExprTest("String.valueOf(1).class", "java.lang.Class<? extends " + JAVA_LANG_STRING + ">");
    inference.get().doCSExprTest("1.getClass()", "java.lang.Class<? extends java.lang.Integer>");
  }

  @Test
  public void testUnknownClass() {
    inference.get().doExprTest("a.class", null);
    inference.get().doCSExprTest("a.class", "java.lang.Class<? extends " + JAVA_LANG_OBJECT + ">");

    inference.get().doExprTest("a().class", null);
    inference.get().doCSExprTest("a().class", "java.lang.Class");
  }

  @Test
  public void testListLiteralType() {
    inference.get().doExprTest("[null]", "java.util.ArrayList");
    inference.get().doExprTest("[\"foo\", \"bar\"]", "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[\"${foo}\", \"${bar}\"]", "java.util.ArrayList<groovy.lang.GString>");
    inference.get().doExprTest("[1, \"a\"]", "java.util.ArrayList<java.io.Serializable>");
  }

  @Test
  public void testListLiteralTypeCS() {
    inference.get().doCSExprTest("[null]", "java.util.List");
    inference.get().doCSExprTest("[\"foo\", \"bar\"]", "java.util.List<" + JAVA_LANG_STRING + ">");
    inference.get().doCSExprTest("[\"${foo}\", \"${bar}\"]", "java.util.List<groovy.lang.GString>");
    inference.get().doCSExprTest("[1, \"a\"]", "java.util.List<java.io.Serializable>");
  }

  @Test
  public void testMapLiteralType() {
    inference.get().doExprTest("[a: 'foo']", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[1: 'foo']", "java.util.LinkedHashMap<java.lang.Integer, " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[1L: 'foo']", "java.util.LinkedHashMap<java.lang.Long, " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[null: 'foo']", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[(null): 'foo']", "java.util.LinkedHashMap<null, " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[foo: null]", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", null>");
    inference.get().doExprTest("[(null): 'foo', bar: null]", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[foo: 'bar', 2: 'goo']", "java.util.LinkedHashMap<java.io.Serializable, " + JAVA_LANG_STRING + ">");
  }

  @Test
  public void testRecursiveLiteralTypes() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    inference.get().doExprTest(" def foo() { [foo()] }\nfoo()", "java.util.ArrayList<" + JAVA_LANG_OBJECT + ">");
    inference.get().doExprTest(" def foo() { [new Object(), foo()] }\nfoo()", "java.util.ArrayList<" + JAVA_LANG_OBJECT + ">");
    inference.get().doExprTest(" def foo() { [someKey1: foo()] }\nfoo()", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", java.util.LinkedHashMap>");
    inference.get().doExprTest(" def foo() { [someKey0: new Object(), someKey1: foo()] }\nfoo()", "java.util.LinkedHashMap<" + JAVA_LANG_STRING + ", " + JAVA_LANG_OBJECT + ">");
  }

  @Test
  public void testRangeLiteralType() {
    inference.get().doExprTest("1..10", "groovy.lang.IntRange");
    inference.get().doExprTest("'a'..'z'", "groovy.lang.Range<" + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("'b'..1", "groovy.lang.Range<java.io.Serializable>");
  }

  @Test
  public void testListWithSpread() {
    inference.get().doExprTest("def l = [1, 2]; [*l]", "java.util.ArrayList<java.lang.Integer>");
    inference.get().doExprTest("def l = [1, 2]; [*[*[*l]]]", "java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void testMapSpreadDotAccess() {
    inference.get().doExprTest("[foo: 2, bar: 4]*.key", "java.util.ArrayList<" + JAVA_LANG_STRING + ">");
    inference.get().doExprTest("[foo: 2, bar: 4]*.value", "java.util.ArrayList<java.lang.Integer>");
    inference.get().doExprTest("[foo: 2, bar: 4]*.undefined", "java.util.List");
  }

  @Test
  public void testInstanceofDoesNotInterfereWithOuterIf() {
    inference.get().doTest("""
              def bar(CharSequence xx) {
                   if (xx instanceof String) {
                         1 instanceof Object
                         <caret>xx.hashCode()
                     }
                 }
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testGenericTupleInferenceWithTypeParam() {
    inference.get().doTest("""
              def <T> T func(T arg){
                     return arg
                 }
             
                 def bar() {
                     def ll = func([[""]])
                     l<caret>l
                 }
             """, "java.util.ArrayList<java.util.ArrayList<" + JAVA_LANG_STRING + ">>");
  }

  @Test
  public void testGenericTupleInferenceWithTypeParam2() {
    inference.get().doTest("""
              def <T> T func(T arg){
                     return arg
                 }
             
                 def bar() {
                     def ll = func([["", 1]])
                     l<caret>l
                 }
             """, "java.util.ArrayList<java.util.ArrayList<java.io.Serializable>>");
  }

  @Test
  public void testEnumValuesType() {
    inference.get().doExprTest("enum E {}; E.values()", "E[]");
  }

  @Test
  public void testClosureOwnerType() {
    inference.get().doTest("""
              class W {
                   def c = {
                         <caret>owner
                     }
                 }
             """, "W");
  }

  @Test
  public void testElvisAssignment() {
    inference.get().doExprTest("def a; a ?= \"hello\"", JAVA_LANG_STRING);
    inference.get().doExprTest("def a = \"\"; a ?= null", JAVA_LANG_STRING);
    inference.get().doExprTest("def a = \"s\"; a ?= 1", "[java.io.Serializable,java.lang.Comparable<? extends java.io.Serializable>]");
  }

  @Test
  public void testSpreadAsImmutable() {
    inference.get().doExprTest("List<List<String>> a; a*.asImmutable()", "java.util.ArrayList<java.util.List<" + JAVA_LANG_STRING + ">>");
  }

  @Test
  public void testDontStartInferenceForMethodParameterType() {
    inference.get().doTest("def bar(String ss) { <caret>ss }", JAVA_LANG_STRING);
  }

  @Test
  public void testClosureParam() {
    inference.get().doTest("""
              interface I { def foo(String s) }
                 def bar(I i) {}
                 bar { var ->
                     <caret>var
                 }
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testAssignmentInCycleIndependentOnIndex() {
    inference.get().doTest("""
             def foo
             for (def i = 1; i < 10; i++) {
                 foo = 2
                 <caret>foo
             }
             """, "java.lang.Integer"
    );
  }

  @Test
  public void testAssignmentInCycleDependingOnIndex() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest("""
             def foo
             for (def i = 1; i < 10; i++) {
                 foo = i
                 <caret>foo
             }
             """, "java.lang.Integer"
    );
  }

  @Test
  public void testJavaFieldPassedAsArgumentToOverloadedMethod() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    myFixture.addClass("public interface I {}");
    myFixture.addClass("public class SuperClass { public I myField; }");
    inference.get().doTest("""
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

  @Test
  public void testNoSoeArgumentInstructionInCycle() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(3, myFixture.getTestRootDisposable());
    inference.get().doTest("""
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

  @Test
  public void testAssignmentToIteratedVariable() {
    inference.get().doTest("""
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

  @Test
  public void testNoSoeWithWriteToIteratedVariableInCycle() {
    inference.get().doTest("""
             while (u) {
                 for (a in b) {
                     b = a
                 }
             }
             <caret>b
             """, null
    );
  }

  @Test
  public void testNoSoeCyclicMultiAssignment() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(4, myFixture.getTestRootDisposable());
    inference.get().doTest("""
             def input = ""
             while (condition) {
                 def (name) = parseOption(input)
                 input = input.substring(<caret>name)
             }
             """, null
    );
  }

  @Test
  public void testStringVariableAssignedWithGStringInsideClosureCS() {
    inference.get().doTest("""
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

  @Test
  public void testSpreadListOfClasses() {
    inference.get().doExprTest("""
                     [String, Integer]*.'class'
                 """, "java.util.ArrayList<java.lang.Class<? extends java.lang.Class>>");
  }

  @Test
  public void testReassignedLocalCS() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    inference.get().doTest("""
                 def aa = "1"
                 a<caret>a.toUpperCase()
                 if (false) {
                     aa = new Object()
                     aa
                 }
                 aa
             """, JAVA_LANG_STRING);

    var file = myFixture.getFile();
    //noinspection ResultOfMethodCallIgnored
    myFixture.getDocument(file).getTextLength();
    ReadAction.runBlocking(() -> {
      var ref = (GrReferenceExpression)file.findReferenceAt(myFixture.getDocument(file).getTextLength() - 2);
      var actual = ref.getType();
      assertType(JAVA_LANG_OBJECT, actual);
    });
  }

  @Test
  public void testFieldWithCallConstraint() {
    inference.get().doTest("""
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

  @Test
  public void testReassignField() {
    inference.get().doTest("""
                 class T {
                     def field = ""
             
                     def m() {
                         field = 1
                         fie<caret>ld
                     }
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testFieldWithCallConstraintCS() {
    inference.get().doTest("""
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

  @Test
  public void testReassignFieldCS() {
    inference.get().doTest("""
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

  @Test
  public void testSpreadCallExpressionInChainCall() {
    inference.get().doTest("""
               [""]*.trim().las<caret>t()
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testSpreadFieldExpressionInChainCall() {
    inference.get().doTest("""
                 class C {
                   public Integer field;
                 }
                 [new C()]*.field.las<caret>t()
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testDoTypeInferenceForVariablesFromOuterContext() {
    inference.get().doTest("""
                 def foo(p) {
                   p = 1
                   def closure = {
                     <caret>p
                   }
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testDoTypeInferenceForOuterVariablesWithExplicitType() {
    inference.get().doTest("""
                 def foo() {
                   Integer x = 1
                   def closure = {
                     <caret>x
                   }
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testDoTypeInferenceForOuterFinalVariables() {
    inference.get().doTest("""
                 def foo() {
                   final def x = "string"
                   def closure = { <caret>x }
                 }
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testDoTypeInferenceForOuterEffectivelyFinalVariables() {
    inference.get().doTest("""
                 def foo() {
                   def x = "string"
                   def closure = { <caret>x }
                 }
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testUseOuterContextForClosuresPassedToDGM() {
    inference.get().doTest("""
                 def foo() {
                     def x = 1
                     'q'.with {
                       <caret>x
                     }
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testUseOuterContextInNestedClosures() {
    inference.get().doTest("""
                 def foo() {
                     def x = 1
                     'q'.with ({
                       def closure = { <caret>x }
                     })
                     x = ""
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testAllowUseOfOuterContextForNestedDGMClosures() {
    inference.get().doTest("""
                 def foo(x) {
                   x = 1
                   def cl1 = 1.with { 2.with { <caret>x } }
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testParenthesizedExpression() {
    inference.get().doTest("""
                 def foo(def p) {
                     def x = 1
                     1.with (({ <caret>x }))
                 }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void testAssignmentInsideClosure() {
    inference.get().doTest("""
                 def foo() {
                   def x = 'q'
                   1.with {
                     x = 1
                   }
                   <caret>x
                 }
             """, "[java.io.Serializable,java.lang.Comparable<? extends java.io.Serializable>]");
  }

  @Test
  public void testAssignmentInsideClosure2() {
    inference.get().doTest("""
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

  @Test
  public void testNoChangesForNullTypeInsideClosure() {
    inference.get().doTest("""
                 def foo() {
                   def x
                   [1].each {
                       x = 1
                   }
                   <caret>x
                 }
             """, null);
  }

  @Test
  public void testOtherStatementsInsideClosure() {
    inference.get().doTest("""
               def method() {
                   def list = []
             
                   [1].each { bar ->
                       bar
                       <caret>list
                 }
               }
             """, "java.util.ArrayList");
  }

  @Test
  public void testUseDFAResultsFromConditionalBranch() {
    inference.get().doTest("""
                 def foo(def bar) {
                     if (bar instanceof String) {
                         10.with {
                             <caret>bar
                         }
                     }
                 }
             """, JAVA_LANG_STRING);
  }

  @Test
  public void testUseMethodCallsInsideClosureBlock() {
    inference.get().doTest("""
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

  @Test
  public void testUseMethodCallsInsideClosureBlock2() {
    inference.get().doTest("""
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

  @Test
  public void testUseMethodCallsInsideClosureBlock3() {
    inference.get().doTest("""
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

  @Test
  public void testDeepNestedInterconnectedVariables() {
    inference.get().doTest("""
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

  @Test
  public void testInstanceofInfluenceOnNestedClosures() {
    inference.get().doTest("""
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
    inference.get().doTest("""
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

  @Test
  public void testSafeNavigation() {
    inference.get().doTest("""
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

  @Test
  public void testAssignmentInsideUnknownClosure() {
    inference.get().doTest("""
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
    inference.get().doTest("""
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
    inference.get().doTest("""
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
    inference.get().doTest("""
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
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(2, myFixture.getTestRootDisposable());
    inference.get().doTest("""
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
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest("""
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
    inference.get().doTest("""
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
    inference.get().doTest("""
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
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest("""
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

  @Test
  public void testFlowTypingReachableThroughClosure() {
    inference.get().doTest("""
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

  @Test
  public void testAssignmentInsideDanglingClosureDoesNotChangeTypeInParallelFlow() {
    inference.get().doTest("""
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

  @Test
  public void testAssignmentInsideDanglingClosureDoesNotChangeTypesBeforeDefinition() {
    inference.get().doTest("""
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
    inference.get().doTest("""
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
    inference.get().doTest("""
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

  @Test
  public void testAssignmentInNestedDanglingClosure2() {
    inference.get().doTest("""
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

  @Test
  public void testShadowedField() {
    inference.get().doTest("""
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

  @Test
  public void testCyclicFlowWithClosure() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContext(2, myFixture.getTestRootDisposable());
    inference.get().doTest("""
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

  @Test
  public void testCycleWithUnknownClosure() {
    //allowNestedContext(2, getTestRootDisposable())
    inference.get().doTest("""
             static  bar(Closure cl) {}
             
             static def foo() {
                 for (def i = 0; i < 10; i = bar { i++ }) {
                   <caret>i
                 }
             }
             """, JAVA_LANG_INTEGER);
  }

  public void _testNoSOEInOperatorUsageWithSharedVariable() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    allowNestedContextOnce(myFixture.getTestRootDisposable());
    inference.get().doTest("""
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

  @Test
  public void testCacheConsistencyForClosuresInCycle() {
    inference.get().doTest("""
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

  @Test
  public void testCacheConsistencyForClosuresInCycle2() {
    inference.get().doTest("""
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

  @Test
  public void testInitialTypeInfluencesDFA() {
    inference.get().doTest("""
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

  @Test
  public void testMixinWithUnknownIdentifier() {
    inference.get().doTest("""
             protected void onLoadConfig (Map configSection) {
                 if (configSection.presetMode != null)
                   setPresetMode(p<caret>resetMode)
             }""", null);
  }

  @Test
  public void testSOEWithLargeFlow() {
    LightJavaCodeInsightTestFixture myFixture = inference.get().getFixture();
    RecursionManager.disableAssertOnRecursionPrevention(myFixture.getTestRootDisposable());
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    allowNestedContext(5, myFixture.getTestRootDisposable());
    inference.get().doTest("""
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

  @Test
  public void testThrowInClosure() {
    inference.get().doTest("""
             static String rootDir(Path archive) {
                 return createTarGzInputStream(archive).withCloseable {
                     <caret>it.nextTarEntry?.name ?: { throw new IllegalStateException("Unable to read $archive") }()
                 }
             }
             """, null);
  }

  @Test
  public void testFlowTypingWithDGM() {
    inference.get().doTest("""
             def <T> Collection<T> aa(Closure<? extends Collection<? extends T>> cl) {
             }
             
             def foo() {
                 def xx = aa { [1] }
                 x<caret>x
             }
             """, "java.util.Collection<java.lang.Integer>");
  }

  @Test
  public void testBoxingOnNullableReceiver() {
    inference.get().doTest("""
             def xx = ""?.length()
             x<caret>x
             """, "java.lang.Integer");
  }
}
