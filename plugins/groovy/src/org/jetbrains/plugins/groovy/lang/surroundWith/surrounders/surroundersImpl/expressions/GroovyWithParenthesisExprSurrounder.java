package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithParenthesisExprSurrounder extends GroovyExpressionSurrounder {
  protected TextRange surroundExpression(GrExpression expression) {
    GrParenthesizedExpression result = (GrParenthesizedExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createExpressionFromText("(a)");
    replaceToOldExpression(result.getOperand(), expression);
    expression.replaceWithExpression(result, true);
    return new TextRange(result.getTextRange().getEndOffset(), result.getTextRange().getEndOffset());
  }

  public String getTemplateDescription() {
    return "(...)";
  }
}
