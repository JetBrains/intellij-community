/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyExpressionSurrounder implements Surrounder {
  protected boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof GrExpression;
  }

  @Override
  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length != 1) return null;

    PsiElement element = elements[0];

    return surroundExpression((GrExpression) element, element.getParent());
  }

  protected abstract TextRange surroundExpression(GrExpression expression, PsiElement context);

  @Override
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return elements.length == 1 &&  isApplicable(elements[0]);
  }

  protected static void replaceToOldExpression(GrExpression oldExpr, GrExpression replacement) {
    oldExpr.replaceWithExpression(replacement, false);
  }
}
