// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class EmptyListSubstitutorInferenceTest implements TypingTest, ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void disableRecursion() {
    runInEdtAndWait(() -> RecursionManager.assertOnRecursionPrevention(getFixture().getTestRootDisposable()));
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

  @Disabled("Requires list literal inference from both arguments and context type")
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
