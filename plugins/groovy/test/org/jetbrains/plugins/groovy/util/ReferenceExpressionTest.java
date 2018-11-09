// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiElement;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType;

public interface ReferenceExpressionTest extends ResolveTest, TypingTest {

  default void referenceExpressionTest(@Language("Groovy") String text, @Nullable Class<? extends PsiElement> resolved, String expectedType) {
    GrReferenceExpression expression = configureByExpression(text, GrReferenceExpression.class);
    resolveTest(expression, resolved);
    assertType(expectedType, getLastExpression().getType());
  }
}
