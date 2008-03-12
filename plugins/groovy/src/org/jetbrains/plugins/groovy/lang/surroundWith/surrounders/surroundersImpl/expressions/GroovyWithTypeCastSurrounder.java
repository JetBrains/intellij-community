package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithTypeCastSurrounder extends GroovyExpressionSurrounder {
  protected TextRange surroundExpression(GrExpression expression) {
    GrParenthesizedExpression parenthesized = (GrParenthesizedExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createTopElementFromText("((Type)a)");
    GrTypeCastExpression typeCast = (GrTypeCastExpression) parenthesized.getOperand();
    replaceToOldExpression(typeCast.getOperand(), expression);
    expression.replaceWithExpression(parenthesized, true);
    GrTypeElement typeElement = typeCast.getCastTypeElement();
    int endOffset = typeElement.getTextRange().getStartOffset();

    typeCast.getNode().removeChild(typeElement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "((Type) ...)";
  }
}
