package org.jetbrains.javafx.refactoring.surround.surrounders.expressions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxValueExpression;
import org.jetbrains.javafx.refactoring.JavaFxChangeUtil;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxExpressionSurrounder implements Surrounder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxExpressionSurrounder");

  @Override
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof JavaFxValueExpression);
    return isApplicable((JavaFxValueExpression)elements[0]);
  }

  protected abstract boolean isApplicable(final JavaFxValueExpression element);

  protected abstract String generateTemplate(final JavaFxElement element);

  @Override
  public TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    final int offset = surroundExpression((JavaFxValueExpression)elements[0]).getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  @NotNull
  protected JavaFxExpression surroundExpression(JavaFxValueExpression element) {
    JavaFxExpression expression =
      JavaFxChangeUtil.createExpressionFromText(element.getProject(), generateTemplate(element));
    element = (JavaFxValueExpression)element.replace(expression);
    element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
    return element;
  }
}
