// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.LightProjectTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.intellij.psi.CommonClassNames.*;

public class SubstitutorInferenceTest extends LightProjectTest implements TypingTest, ResolveTest {
  @Override
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK;
  }

  @Before
  public void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
  }

  @Before
  public void addClasses() {
    getFixture().addFileToProject("classes.groovy",
                                  """
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
                                    """);
  }

  @Test
  public void rawInVariableInitializer() {
    typingTest("List<String> l = <caret>new ArrayList()", GrNewExpression.class, "java.util.ArrayList");
  }

  @Test
  public void explicitInVariableInitializer() {
    typingTest("List<String> l = <caret>new ArrayList<Integer>()", GrNewExpression.class, "java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void diamondInVariableInitializer() {
    GrNewExpression expression = elementUnderCaret("I<PG> l = <caret>new C<>()", GrNewExpression.class);
    LightGroovyTestCase.assertType("C<PG>", expression.getType());
    MethodResolveResult resolved = (MethodResolveResult)expression.advancedResolve();
    PsiTypeParameter typeParameter = resolved.getElement().getContainingClass().getTypeParameters()[0];
    LightGroovyTestCase.assertType("PG", resolved.getSubstitutor().substitute(typeParameter));
  }

  @Test
  public void diamondInTupleVariableInitializer() {
    typingTest("def (I<String> l) = [new<caret> C<>()]", GrNewExpression.class, "C<java.lang.String>");
  }

  @Test
  public void diamondInTupleAssignmentInitializer() {
    typingTest("I<String> l; (l) = [new<caret> C<>()]", GrNewExpression.class, "C<java.lang.String>");
  }

  @Test
  public void diamondInArgumentOfDiamondInVariableInitializer() {
    typingTest("I<PG> l = new<caret> C<>(new C<>())", GrNewExpression.class, "C<PG>");
  }

  @Test
  public void diamondInArgumentOfDiamondInVariableInitializer2() {
    typingTest("I<PG> l = new C<>(new<caret> C<>())", GrNewExpression.class, "C<PG>");
  }

  @Test
  public void diamondInNewExpression() {
    typingTest("new C<PG>(new<caret> C<>())", GrNewExpression.class, "C<PG>");
  }

  @Test
  public void diamondTypeFromArgument() {
    expressionTypeTest("new C<>(new C<Integer>())", "C<java.lang.Integer>");
  }

  @Test
  public void callInArgumentOfDiamondInVariableInitializer() {
    typingTest(
      """
        def <T> T theMethod() {}
        I<PG> l = new <caret> C<>(theMethod())
        """, GrNewExpression.class, "C<PG>");
  }

  @Test
  public void callInArgumentOfDiamondInVariableInitializer2() {
    typingTest("""
                 def <T> T theMethod() {}
                 I<PG> l = new C<>(theMethod<caret>())
                 """, GrMethodCall.class, "I<? extends PG>");
  }

  @Test
  public void callInArgumentOfCallInVariableInitializer() {
    typingTest("""
                 def <T> T theMethod() {}
                 def <T> T first(List<T> arg) {}
                 I<PG> l = <caret>first(theMethod())
                 """, GrMethodCall.class, "I<PG>");
  }

  @Test
  public void callInArgumentOfCallInVariableInitializer2() {
    typingTest("""
                 def <T> T theMethod() {}
                 def <T> T first(List<T> arg) {}
                 I<PG> l = first(theMethod<caret>())
                 """, GrMethodCall.class, "java.util.List<I<PG>>");
  }

  @Test
  public void closureSafeCastAsArgumentOfMethod() {
    typingTest("""
                 interface F<T,U> { U foo(T arg); }              // T -> U
                 interface G<V,X> extends F<List<V>, List<X>> {} // List<V> -> List<X>
                 void foo(F<List<String>, List<Integer>> f) {}
                 foo({} <caret>as G)
                 """, GrSafeCastExpression.class, "G<java.lang.String,java.lang.Integer>");
  }

  @Test
  public void closureSafeCastAsArgumentOfDiamondConstructor() {
    typingTest("""
                 interface F<T,U> { U foo(T arg); }
                 abstract class Wrapper<V, X> implements F<V, X> {
                   Wrapper(F<V, X> wrappee) {}
                 }
                 F<Integer, String> w = new Wrapper<>({} <caret>as F)
                 """, GrSafeCastExpression.class, "F<java.lang.Integer,java.lang.String>");
  }

  @Test
  public void explicitClosureSafeCastAsArgumentOfGenericMethod() {
    typingTest("""
                 interface Producer<T> {}
                 static <T> T ppp(Producer<T> p) {}
                 <caret>ppp({} as Producer<String>)
                 """, GrMethodCall.class, "java.lang.String");
  }

  @Test
  public void nonClosureSafeCast() {
    GrSafeCastExpression expression = elementUnderCaret("\"hi\" <caret>as Number", GrSafeCastExpression.class);
    assertSubstitutor(expression.getReference().advancedResolve(), JAVA_LANG_NUMBER);
  }

  @Test
  public void implicitCallInVariableInitializer() {
    typingTest("String s = <caret>new IdCallable()()", GrMethodCall.class, "java.lang.String");
  }

  @Test
  public void implicitCallInArgumentOfDiamondInVariableInitializer() {
    typingTest("C<Integer> s = new C<>(<caret>new IdCallable()())", GrMethodCall.class, "I<? extends java.lang.Integer>");
  }

  @Test
  public void implicitCallFromArgument() {
    expressionTypeTest("new IdCallable()(\"hi\")", "java.lang.String");
  }

  @Test
  public void varargMethodCallTypeFromArgument() {
    expressionTypeTest("static <T> List<T> foo(T... t) {}; foo(\"\")", "java.util.List<java.lang.String>");
    expressionTypeTest("static <T> List<T> foo(T... t) {}; foo(1d, 2l)", "java.util.List<java.lang.Number>");
  }

  @Test
  public void varargMethodCallTypeFromArrayArgument() {
    expressionTypeTest("static <T> List<T> foo(T... t) {}; foo(\"\".split(\"\"))", "java.util.List<java.lang.String>");
  }

  @Ignore("Requires list literal inference from both arguments and context type")
  @Test
  public void diamondFromOuterListLiteral() {
    typingTest("List<List<String>> l = [new <caret>ArrayList<>()]", GrNewExpression.class, "java.util.ArrayList<java.lang.String>");
  }

  /**
   * This test is wrong and exists only to preserve behaviour
   * and should fail when 'diamond from outer list literal' will pass.
   */
  @Test
  public void listLiteralWithDiamond() {
    typingTest("List<List<String>> l = [new <caret>ArrayList<>()]", GrNewExpression.class, "java.util.ArrayList<java.lang.Object>");
    typingTest("List<List<String>> l = <caret>[new ArrayList<>()]", GrListOrMap.class,
               "java.util.ArrayList<java.util.ArrayList<java.lang.Object>>");
  }

  @Test
  public void emptyMapLiteralInVariableInitializer() {
    typingTest("Map<String, Integer> m = <caret>[:]", GrListOrMap.class, "java.util.LinkedHashMap<java.lang.String, java.lang.Integer>");
  }

  @Test
  public void genericGetterFromLeftType() {
    GrReferenceExpression ref =
      elementUnderCaret("I<PG> lp = new GenericPropertyContainer().<caret>genericProperty", GrReferenceExpression.class);
    assertSubstitutor(ref.advancedResolve(), "PG");
  }

  @Test
  public void genericSetterFromArgument() {
    GrReferenceExpression ref =
      elementUnderCaret("new GenericPropertyContainer().<caret>genericProperty = new C<String>()", GrReferenceExpression.class);
    assertSubstitutor(ref.advancedResolve(), "java.lang.String");
  }

  @Test
  public void plusAssignment() {
    GrAssignmentExpression op = elementUnderCaret("new Files().files <caret>+= new File(\".\")", GrAssignmentExpression.class);
    assertSubstitutor(op.getReference().advancedResolve(), "java.io.File");
  }

  @Test
  public void plusAssignmentGenericPropertyRValue() {
    //RecursionManager.disableAssertOnRecursionPrevention(fixture.testRootDisposable)
    //RecursionManager.disableMissedCacheAssertions(fixture.testRootDisposable)
    GrReferenceExpression ref =
      elementUnderCaret("new GenericPropertyContainer().<caret>genericList += new ArrayList<String>()", GrReferenceExpression.class);
    assertSubstitutor(ref.getRValueReference().advancedResolve(), JAVA_LANG_STRING);
  }

  @Test
  public void plusAssignmentGenericProperty() {
    RecursionManager.disableAssertOnRecursionPrevention(getFixture().getTestRootDisposable());
    RecursionManager.disableMissedCacheAssertions(getFixture().getTestRootDisposable());
    GrAssignmentExpression op =
      elementUnderCaret("new GenericPropertyContainer().genericList <caret>+= new ArrayList<String>()", GrAssignmentExpression.class);
    assertSubstitutor(op.getReference().advancedResolve(), JAVA_LANG_STRING);
  }

  @Ignore("we don't yet infer l-value substitutors")
  @Test
  public void plusAssignmentGenericPropertyLValue() {
    GrReferenceExpression ref =
      elementUnderCaret("new GenericPropertyContainer().<caret>genericList += new ArrayList<String>()", GrReferenceExpression.class);
    assertSubstitutor(ref.getLValueReference().advancedResolve(), JAVA_LANG_STRING);
  }

  @Test
  public void plusAssignmentWithIndexRValue() {
    GrIndexProperty op = elementUnderCaret("Map<Number, String> mns; mns<caret>[42] += \"foo\"", GrIndexProperty.class);
    assertSubstitutor(op.getRValueReference().advancedResolve(), JAVA_LANG_NUMBER, JAVA_LANG_STRING);
  }

  @Test
  public void plusAssignmentWithIndexLValue() {
    GrIndexProperty op = elementUnderCaret("Map<Number, String> mns; mns<caret>[42] += \"foo\"", GrIndexProperty.class);
    assertSubstitutor(op.getLValueReference().advancedResolve(), JAVA_LANG_NUMBER, JAVA_LANG_STRING);
  }

  @Test
  public void sameMethodNested() {
    GrMethodCall call = elementUnderCaret("""
                                            static <T> T run(Closure<T> c) {}
                                            <caret>run(run { return { 42 } })
                                            """, GrMethodCall.class);
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_INTEGER);
  }

  @Test
  public void chainedWith() {
    resolveTest("""
                  class A { def aMethod() { "42" } }
                  "bar".with { new A() }.with { it.<caret>aMethod() }
                  """, GrMethod.class);
  }

  @Test
  public void collectorsToList() {
    GrMethodCall call = elementUnderCaret("""
                                            static void testCode(java.util.stream.Stream<Integer> ss) {
                                              ss.collect(java.util.stream.Collectors.<caret>toList())
                                            }
                                            """, GrMethodCall.class);
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_INTEGER);
  }

  @Test
  public void staticCallWithRawArgumentWithLeftType() {
    GrMethodCall call = elementUnderCaret("static <T> T lll(List<T> l) {}; List l; Date d = <caret>lll(l)", GrMethodCall.class);
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_OBJECT);
  }

  @Test
  public void dgmCallOnRawReceiverWithLeftType() {
    GrMethodCall call = elementUnderCaret("List l; Date d = l.<caret>getAt(0)", GrMethodCall.class);
    assertSubstitutor(call.advancedResolve(), JAVA_LANG_OBJECT);
  }

  private static void assertSubstitutor(GroovyResolveResult result, String... expectedTypes) {
    PsiTypeParameterListOwner element = (PsiTypeParameterListOwner)result.getElement();
    PsiTypeParameter[] typeParameters = element.getTypeParameters();
    Assert.assertEquals(typeParameters.length, expectedTypes.length);
    PsiSubstitutor substitutor = result.getSubstitutor();
    for (int i = 0; i < expectedTypes.length; i++) {
      LightGroovyTestCase.assertType(expectedTypes[i], substitutor.substitute(typeParameters[i]));
    }
  }
}
