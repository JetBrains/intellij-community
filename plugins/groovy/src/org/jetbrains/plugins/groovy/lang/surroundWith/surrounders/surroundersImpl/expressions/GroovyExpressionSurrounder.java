package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySingleElementSurrounder;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyExpressionSurrounder extends GroovySingleElementSurrounder {
  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrExpression;
  }

  @Nullable
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length != 1) return null;

    PsiElement element = elements[0];

    return surroundExpression((GrExpression) element);
  }

  protected abstract TextRange surroundExpression(GrExpression expression);

  protected void replaceToOldExpression(GrExpression oldExpr, GrExpression replacement) {
    oldExpr.replaceWithExpression(replacement, false);
  }
}
