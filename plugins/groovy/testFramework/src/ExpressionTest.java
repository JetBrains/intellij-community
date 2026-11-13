// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.testFramework;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Arrays;

import static com.intellij.groovy.testFramework.GroovyAssertions.assertOneElement;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    GroovyResolveResult[] results = ReadAction.computeBlocking(() -> expression.multiResolveGroovy(false));
    PsiElement resolved = assertOneElement(results).getElement();
    return assertInstanceOf(clazz, resolved);
  }

  default <T extends PsiMethod> T methodCallTest(@Nullable Class<T> clazz, @Nullable String expectedType) {
    GrMethodCall expression = elementUnderCaret(GrMethodCall.class);
    GroovyResolveResult[] results = ReadAction.computeBlocking(() -> expression.multiResolveGroovy(false));
    final T resolved;
    if (clazz == null) {
      assertTrue(results.length == 0, () -> ReadAction.computeBlocking(() -> Arrays.toString(results)));
      resolved = null;
    }
    else {
      resolved = assertInstanceOf(clazz, assertOneElement(results).getElement());
    }
    typingTest(expression, expectedType);
    return resolved;
  }
}
