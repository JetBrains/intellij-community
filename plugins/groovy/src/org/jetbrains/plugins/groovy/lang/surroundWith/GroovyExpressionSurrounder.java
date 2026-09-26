// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public abstract class GroovyExpressionSurrounder implements Surrounder {
  protected boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof GrExpression;
  }

  @Override
  public @Nullable TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements) {
    if (elements.length != 1) return null;

    PsiElement element = elements[0];

    return surroundExpression((GrExpression) element, element.getParent());
  }

  protected abstract TextRange surroundExpression(@NotNull GrExpression expression, @Nullable PsiElement context);

  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return elements.length == 1 &&  isApplicable(elements[0]);
  }

  protected static void replaceToOldExpression(GrExpression oldExpr, GrExpression replacement) {
    oldExpr.replaceWithExpression(replacement, false);
  }
}
