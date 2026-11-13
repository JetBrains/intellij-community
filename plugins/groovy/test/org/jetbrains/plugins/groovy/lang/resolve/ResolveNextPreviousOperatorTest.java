// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class ResolveNextPreviousOperatorTest implements ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @BeforeEach
  public void addClasses() {
    getFixture().addFileToProject("classes.groovy",
                                  """
                                    class P {
                                      A getProp() { println "$this: getProp"; new A() }
                                      void setProp(B b) { println "$this: setProp $b" }
                                    
                                      A getAt(int i) { println "$this: getAt $i"; new A() }
                                      void putAt(int i, B b) { println "$this: putAt $i $b" }
                                    }
                                    
                                    class A {
                                      B next() { println "$this: next"; new B() }
                                    }
                                    
                                    class B {}
                                    """);
  }

  @Test
  public void postfixGetterSetter() {
    GrReferenceExpression expression = elementUnderCaret("new P().<caret>prop++", GrReferenceExpression.class);
    ReadAction.run(() -> {
      GroovyReference rValueReference = expression.getRValueReference();

      GroovyResolveResult rResult = rValueReference.advancedResolve();
      Assertions.assertTrue(rResult instanceof AccessorResolveResult);
      Assertions.assertTrue(rResult.isValidResult());
      Assertions.assertEquals("getProp", ((AccessorResolveResult)rResult).getElement().getName());

      GroovyReference lValueReference = expression.getLValueReference();
      GroovyResolveResult lResult = lValueReference.advancedResolve();
      Assertions.assertTrue(lResult instanceof AccessorResolveResult);
      Assertions.assertTrue(lResult.isValidResult());
      Assertions.assertEquals("setProp", ((AccessorResolveResult)lResult).getElement().getName());
    });
  }

  @Test
  public void prefixGetterSetter() {
    GrReferenceExpression expression = elementUnderCaret("++new P().<caret>prop", GrReferenceExpression.class);

    ReadAction.run(() -> {
      GroovyReference rValueReference = expression.getRValueReference();
      GroovyResolveResult rResult = rValueReference.advancedResolve();
      Assertions.assertTrue(rResult instanceof AccessorResolveResult);
      Assertions.assertTrue(rResult.isValidResult());
      Assertions.assertEquals("getProp", ((AccessorResolveResult)rResult).getElement().getName());

      GroovyReference lValueReference = expression.getLValueReference();
      GroovyResolveResult lResult = lValueReference.advancedResolve();
      Assertions.assertTrue(lResult instanceof AccessorResolveResult);
      Assertions.assertTrue(lResult.isValidResult());
      Assertions.assertEquals("setProp", ((AccessorResolveResult)lResult).getElement().getName());
    });
  }

  @Test
  public void postfixIndexGetPut() {
    GrIndexProperty expression = elementUnderCaret("new P()<caret>[42]++", GrIndexProperty.class);
    ReadAction.run(() -> {
      GroovyMethodCallReference rValueReference = expression.getRValueReference();
      GroovyResolveResult rResult = rValueReference.advancedResolve();
      PsiElement rElement = rResult.getElement();
      Assertions.assertTrue(rElement instanceof PsiMethod);
      Assertions.assertTrue(rResult.isValidResult());
      Assertions.assertEquals("getAt", ((PsiMethod)rElement).getName());

      GroovyMethodCallReference lValueReference = expression.getLValueReference();
      GroovyResolveResult lResult = lValueReference.advancedResolve();
      PsiElement lElement = lResult.getElement();
      Assertions.assertTrue(lElement instanceof PsiMethod);
      Assertions.assertTrue(lResult.isValidResult());
      Assertions.assertEquals("putAt", ((PsiMethod)lElement).getName());
    });
  }

  @Test
  public void prefixIndexGetPut() {
    GrIndexProperty expression = elementUnderCaret("++new P()<caret>[42]", GrIndexProperty.class);
    ReadAction.run(() -> {
      GroovyMethodCallReference rValueReference = expression.getRValueReference();
      GroovyResolveResult rResult = rValueReference.advancedResolve();
      PsiElement element = rResult.getElement();
      Assertions.assertTrue(element instanceof PsiMethod);
      Assertions.assertTrue(rResult.isValidResult());
      Assertions.assertEquals("getAt", ((PsiMethod)element).getName());

      GroovyMethodCallReference lValueReference = expression.getLValueReference();
      GroovyResolveResult lResult = lValueReference.advancedResolve();
      PsiElement rElement = lResult.getElement();
      Assertions.assertTrue(rElement instanceof PsiMethod);
      Assertions.assertTrue(lResult.isValidResult());
      Assertions.assertEquals("putAt", ((PsiMethod)rElement).getName());
    });
  }
}
