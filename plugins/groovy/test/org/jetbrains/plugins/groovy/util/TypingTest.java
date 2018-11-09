// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType;

public interface TypingTest extends BaseTest {

  default void typingTest(@Language("Groovy") String text, @Nullable String expectedType) {
    typingTest(configureByExpression(text), expectedType);
  }

  default void typingTest(@NotNull GrExpression expression, @Nullable String expectedType) {
    assertType(expectedType, expression.getType());
  }
}
