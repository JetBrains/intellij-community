// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ResolveNextPreviousOperatorTest extends GroovyLatestTest implements ResolveTest {
  @Before
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
    GroovyReference rValueReference = expression.getRValueReference();

    GroovyResolveResult rResult = rValueReference.advancedResolve();
    Assert.assertTrue(rResult instanceof AccessorResolveResult);
    Assert.assertTrue(rResult.isValidResult());
    Assert.assertEquals("getProp", ((AccessorResolveResult)rResult).getElement().getName());

    GroovyReference lValueReference = expression.getLValueReference();
    GroovyResolveResult lResult = lValueReference.advancedResolve();
    Assert.assertTrue(lResult instanceof AccessorResolveResult);
    Assert.assertTrue(lResult.isValidResult());
    Assert.assertEquals("setProp", ((AccessorResolveResult)lResult).getElement().getName());
  }

  @Test
  public void prefixGetterSetter() {
    GrReferenceExpression expression = elementUnderCaret("++new P().<caret>prop", GrReferenceExpression.class);

    GroovyReference rValueReference = expression.getRValueReference();
    GroovyResolveResult rResult = rValueReference.advancedResolve();
    Assert.assertTrue(rResult instanceof AccessorResolveResult);
    Assert.assertTrue(rResult.isValidResult());
    Assert.assertEquals("getProp", ((AccessorResolveResult)rResult).getElement().getName());

    GroovyReference lValueReference = expression.getLValueReference();
    GroovyResolveResult lResult = lValueReference.advancedResolve();
    Assert.assertTrue(lResult instanceof AccessorResolveResult);
    Assert.assertTrue(lResult.isValidResult());
    Assert.assertEquals("setProp", ((AccessorResolveResult)lResult).getElement().getName());
  }

  @Test
  public void postfixIndexGetPut() {
    GrIndexProperty expression = elementUnderCaret("new P()<caret>[42]++", GrIndexProperty.class);
    GroovyMethodCallReference rValueReference = expression.getRValueReference();
    GroovyResolveResult rResult = rValueReference.advancedResolve();
    PsiElement rElement = rResult.getElement();
    Assert.assertTrue(rElement instanceof PsiMethod);
    Assert.assertTrue(rResult.isValidResult());
    Assert.assertEquals("getAt", ((PsiMethod)rElement).getName());

    GroovyMethodCallReference lValueReference = expression.getLValueReference();
    GroovyResolveResult lResult = lValueReference.advancedResolve();
    PsiElement lElement = lResult.getElement();
    Assert.assertTrue(lElement instanceof PsiMethod);
    Assert.assertTrue(lResult.isValidResult());
    Assert.assertEquals("putAt", ((PsiMethod)lElement).getName());
  }

  @Test
  public void prefixIndexGetPut() {
    GrIndexProperty expression = elementUnderCaret("++new P()<caret>[42]", GrIndexProperty.class);
    GroovyMethodCallReference rValueReference = expression.getRValueReference();
    GroovyResolveResult rResult = rValueReference.advancedResolve();
    PsiElement element = rResult.getElement();
    Assert.assertTrue(element instanceof PsiMethod);
    Assert.assertTrue(rResult.isValidResult());
    Assert.assertEquals("getAt", ((PsiMethod)element).getName());

    GroovyMethodCallReference lValueReference = expression.getLValueReference();
    GroovyResolveResult lResult = lValueReference.advancedResolve();
    PsiElement rElement = lResult.getElement();
    Assert.assertTrue(rElement instanceof PsiMethod);
    Assert.assertTrue(lResult.isValidResult());
    Assert.assertEquals("putAt", ((PsiMethod)rElement).getName());
  }
}
