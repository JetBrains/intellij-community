// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static com.intellij.testFramework.UsefulTestCase.*;

public interface ExpressionTest extends ResolveTest, TypingTest {

  default void referenceExpressionTest(@Language("Groovy") String text,
                                       @Nullable Class<? extends PsiElement> resolved,
                                       String expectedType) {
    GrReferenceExpression expression = lastExpression(text, GrReferenceExpression.class);
    resolveTest(expression, resolved);
    typingTest(expression, expectedType);
  }

  default <T extends PsiElement> T referenceExpressionTest(@Nullable Class<T> clazz, @Nullable String expectedType) {
    GrReferenceExpression expression = elementUnderCaret(GrReferenceExpression.class);
    T resolved = resolveTest(expression, clazz);
    typingTest(expression, expectedType);
    return resolved;
  }

  @NotNull
  default <T extends PsiMethod> T resolveMethodTest(@NotNull Class<T> clazz) {
    GrMethodCall expression = elementUnderCaret(GrMethodCall.class);
    GroovyResolveResult[] results = expression.multiResolve(false);
    PsiElement resolved = assertOneElement(results).getElement();
    return assertInstanceOf(resolved, clazz);
  }

  default <T extends PsiMethod> T methodCallTest(@Nullable Class<T> clazz, @Nullable String expectedType) {
    GrMethodCall expression = elementUnderCaret(GrMethodCall.class);
    GroovyResolveResult[] results = expression.multiResolve(false);
    final T resolved;
    if (clazz == null) {
      assertEmpty(results);
      resolved = null;
    }
    else {
      resolved = assertInstanceOf(assertOneElement(results).getElement(), clazz);
    }
    typingTest(expression, expectedType);
    return resolved;
  }
}
