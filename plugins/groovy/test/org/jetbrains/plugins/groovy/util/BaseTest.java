// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Objects;

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf;
import static com.intellij.util.ArrayUtil.getLastElement;

public interface BaseTest {

  @NotNull
  CodeInsightTestFixture getFixture();

  @NotNull
  default Project getProject() {
    return getFixture().getProject();
  }

  @NotNull
  default GroovyFile getGroovyFile() {
    return (GroovyFile)getFixture().getFile();
  }

  @NotNull
  default GroovyFile configureByText(@NotNull String text) {
    getFixture().configureByText("_.groovy", text);
    return getGroovyFile();
  }

  @NotNull
  default <T extends PsiElement> T elementUnderCaret(@NotNull String text, @NotNull Class<T> clazz) {
    configureByText(text);
    return elementUnderCaret(clazz);
  }

  @NotNull
  default <T extends PsiElement> T elementUnderCaret(@NotNull Class<T> clazz) {
    T element = getParentOfType(getFixture().getFile().findElementAt(getFixture().getCaretOffset()), clazz);
    return Objects.requireNonNull(element);
  }

  @NotNull
  default <T extends GrExpression> T lastExpression(@NotNull String text, Class<T> clazz) {
    return clazz.cast(lastExpression(text));
  }

  @NotNull
  default GrExpression lastExpression(@NotNull String text) {
    configureByText(text);
    return lastExpression();
  }

  @NotNull
  default GrExpression lastExpression() {
    final GrStatement lastStatement = getLastElement(getGroovyFile().getStatements());
    return assertInstanceOf(lastStatement, GrExpression.class);
  }
}
