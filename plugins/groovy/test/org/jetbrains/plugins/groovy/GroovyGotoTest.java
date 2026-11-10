// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GroovyGotoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "goto/");
  }

  private void doTest(Condition<PsiElement> verifier) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(verifier, myFixture.getTestName(false) + ".groovy");
  }

  private void doTest(Condition<PsiElement> verifier, String... files) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    for (String file : files) {
      myFixture.configureByFile(file);
    }
    final TargetElementUtil targetUtil = TargetElementUtil.getInstance();
    ReadAction.runBlocking(() -> {
      final PsiElement target = TargetElementUtil.findTargetElement(myFixture.getEditor(), targetUtil.getReferenceSearchFlags());
      assertTrue(verifier.value(target));
    });
  }

  @Test
  public void testNewExpression() {
    doTest(element -> element instanceof GrMethod && ((GrMethod)element).isConstructor() && ((GrMethod)element).getParameters().length == 0);
  }

  @Test
  public void testNewExpressionWithNamedArgs() {
    doTest(element -> element instanceof PsiClass);
  }

  @Test
  public void testNewExpressionWithMapParameter() {
    doTest(element -> element instanceof GrMethod && ((GrMethod)element).isConstructor() && ((GrMethod)element).getParameters().length == 1);
  }

  @Test
  public void testNewExpressionWithAnonymousClass() {
    doTest(element -> element instanceof GrMethod && ((GrMethod)element).isConstructor() && ((GrMethod)element).getParameters().length == 2);
  }

  @Test
  public void testGroovyDocParameter1() {
    doTest(element -> element instanceof GrParameter && ((GrParameter)element).getName().equals("x"));
  }

  @Test
  public void testGroovyDocParameter2() {
    doTest(element -> element instanceof GrParameter && ((GrParameter)element).getName().equals("x"));
  }

  @Test
  public void testConstructorWithSuperClassSameName() {
    doTest(element -> element instanceof PsiMethod && "p2.MyClass".equals(((PsiMethod)element).getContainingClass().getQualifiedName()), "p/MyClass.groovy", "p2/MyClass.groovy");
  }
}
