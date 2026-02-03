// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.jetbrains.plugins.groovy.util.TypingTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class EmptyListSubstitutorInferenceTest extends GroovyLatestTest implements TypingTest, ResolveTest {

  @Before
  public void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable());
  }

  @Test
  public void simple() {
    typingTest("[<caret>]", GrListOrMap.class, "java.util.ArrayList");
  }

  @Test
  public void inUntypedVariableInitializer() {
    typingTest("def l = [<caret>]", GrListOrMap.class, "java.util.ArrayList");
    typingTest("def l = []; <caret>l", GrReferenceExpression.class, "java.util.ArrayList");
  }

  @Test
  public void inRawVariableInitializer() {
    typingTest("List l = [<caret>]", GrListOrMap.class, "java.util.ArrayList");
    typingTest("List l = []; <caret>l", GrReferenceExpression.class, "java.util.ArrayList");
  }

  @Test
  public void inTypedVariableInitializer() {
    typingTest("List<Integer> l = [<caret>]", GrListOrMap.class, "java.util.ArrayList<java.lang.Integer>");
    typingTest("List<Integer> l = []; <caret>l", GrReferenceExpression.class, "java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void inWronglyTypedVariableInitializer() {
    typingTest("int l = [<caret>]", GrListOrMap.class, "java.util.ArrayList");
  }

  @Test
  public void receiverOfDgmMethod() {
    typingTest("[<caret>].each {}", GrListOrMap.class, "java.util.ArrayList");
  }

  @Test
  public void receiverOfDgmMethodInUntypedVariableInitializer() {
    typingTest("def s = [<caret>].each {}", GrListOrMap.class, "java.util.ArrayList");
  }

  @Test
  public void receiverOfDgmMethodInRawVariableInitializer() {
    typingTest("List l = [<caret>].each {}", GrListOrMap.class, "java.util.ArrayList");
  }

  @Test
  public void receiverOfDgmMethodInTypedVariableInitializer() {
    typingTest("List<Integer> l = [<caret>].each {}", GrListOrMap.class, "java.util.ArrayList");
  }

  @Test
  public void inArgumentOfNewExpression() {
    typingTest("new ArrayList<Integer>([<caret>])", GrListOrMap.class, "java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void inArgumentOfDiamondNewExpression() {
    typingTest("new ArrayList<Integer>(new ArrayList<>([<caret>]))", GrListOrMap.class, "java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void inArgumentOfNestedDiamondNewExpressionInVariableInitializer() {
    typingTest("List<Integer> l = new ArrayList<>(new ArrayList<>([<caret>]))", GrListOrMap.class,
               "java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void inArgumentOfGenericMethodCall() {
    typingTest("def <T> T id(T a) {a}; id([<caret>])", GrListOrMap.class, "java.util.ArrayList");
    typingTest("def <T> T id(T a) {a}; <caret>id([])", GrMethodCall.class, "java.util.ArrayList");
  }

  @Test
  public void inArgumentOfGenericMethodCallWithArgument() {
    typingTest("def <T> List<T> add(List<T> l, T v) {}; add([<caret>], 1)", GrListOrMap.class, "java.util.ArrayList<java.lang.Integer>");
    typingTest("def <T> List<T> add(List<T> l, T v) {}; <caret>add([], 1)", GrMethodCall.class, "java.util.List<java.lang.Integer>");
  }

  @Ignore("Requires list literal inference from both arguments and context type")
  @Test
  public void emptyListLiteralFromOuterListLiteral() {
    typingTest("List<List<Integer>> l = [[<caret>]]", GrListOrMap.class, "java.util.List<java.lang.Integer>");
  }

  /**
   * This test is wrong and exists only to preserve behaviour
   * and should fail when 'empty list literal from outer list literal' will pass.
   */
  @Test
  public void listLiteralWithEmptyListLiteral() {
    typingTest("List<List<Integer>> l = <caret>[[]]", GrListOrMap.class, "java.util.ArrayList<java.util.ArrayList>");
    typingTest("List<List<Integer>> l = [[<caret>]]", GrListOrMap.class, "java.util.ArrayList");
  }
}
