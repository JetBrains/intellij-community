package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyConditionSurrounder;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWhileExprSurrounder extends GroovyConditionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "while " + "(" + node.getText() + ") { 4 \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrWhileStatement;

    GrWhileStatement grWhileStatement = (GrWhileStatement) element;
    GrCondition grStatement = grWhileStatement.getBody();

    int endOffset = grStatement.getTextRange().getEndOffset();

    if (grStatement instanceof GrStatement &&
        !(grStatement instanceof GrBlockStatement)) {
      endOffset = grStatement.getTextRange().getEndOffset();
      grStatement.getParent().getNode().removeChild(grStatement.getNode());
    } else if (grStatement instanceof GrBlockStatement) {
      GrStatement grStatementInBody = ((GrBlockStatement) grStatement).getBlock().getStatements()[0];
      endOffset = grStatementInBody.getTextRange().getEndOffset();
      grStatementInBody.getParent().getNode().removeChild(grStatementInBody.getNode());
    }

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "while (...) {}";
  }
}
