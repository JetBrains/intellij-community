package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithTypeCastSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return isNeedsParentheses(node) ? "(" + "(" + "Type" + ") " + "(" + node.getText() + ")" + ")"
        : "(" + "(" + "Type" + ") " + node.getText() + ")";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrParenthesizedExpression;

    GrParenthesizedExpression grParenthesizedExpression = (GrParenthesizedExpression) element;
    GrTypeCastExpression typeCast = (GrTypeCastExpression) grParenthesizedExpression.getOperand();

    GrTypeElement grTypeElement = typeCast.getCastTypeElement();
    int endOffset = grTypeElement.getTextRange().getStartOffset();

    typeCast.getNode().removeChild(grTypeElement.getNode());

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "((Type) ...)";
  }
}
