// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Objects;

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

public interface BaseTest {

  @NotNull
  CodeInsightTestFixture getFixture();

  default GroovyFile configureByText(@NotNull String text) {
    return (GroovyFile)getFixture().configureByText("_.groovy", text);
  }

  default GrExpression configureByExpression(@NotNull String text) {
    return (GrExpression)ArrayUtil.getLastElement(configureByText(text).getStatements());
  }

  @NotNull
  default <T extends PsiElement> T elementUnderCaret(@NotNull String text, @NotNull Class<T> clazz) {
    GroovyFile file = configureByText(text);
    T element = getParentOfType(file.findElementAt(getFixture().getCaretOffset()), clazz);
    return Objects.requireNonNull(element);
  }
}
