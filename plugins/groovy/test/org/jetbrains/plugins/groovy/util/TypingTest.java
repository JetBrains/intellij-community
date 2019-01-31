// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType;

public interface TypingTest extends BaseTest {

  default void expressionTypeTest(@Language("Groovy") @NotNull String text, @Nullable String expectedType) {
    typingTest(lastExpression(text), expectedType);
  }

  default void typingTest(@NotNull String text, @Nullable String expectedType) {
    typingTest(elementUnderCaret(text, GrExpression.class), expectedType);
  }

  default void typingTest(@NotNull String text, @NotNull Class<? extends GrExpression> clazz, @Nullable String expectedType) {
    typingTest(elementUnderCaret(text, clazz), expectedType);
  }

  default void typingTest(@NotNull GrExpression expression, @Nullable String expectedType) {
    assertType(expectedType, expression.getType());
  }
}
