package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySingleElementSurrounder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;

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
    oldExpr.replaceWithExpression((GrExpression) replacement.copy(), true);
  }
}
