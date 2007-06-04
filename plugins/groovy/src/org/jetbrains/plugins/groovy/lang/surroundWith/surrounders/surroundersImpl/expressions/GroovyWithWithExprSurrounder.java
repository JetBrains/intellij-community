package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWithStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWithExprSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "with" + "(" + node.getText() + ") { 4 \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrWithStatement;

    GrWithStatement grWithStatement = (GrWithStatement) element;

    GrCondition grStatement = grWithStatement.getBody();

    int endOffset = grStatement.getTextRange().getEndOffset();

    if (grStatement instanceof GrStatement) {
      endOffset = grStatement.getTextRange().getEndOffset();
      grStatement.getParent().getNode().removeChild(grStatement.getNode());

    } else if (grStatement instanceof GrOpenBlock) {
      GrStatement grStatementInBody = ((GrOpenBlock) grStatement).getStatements()[0];
      endOffset = grStatementInBody.getTextRange().getEndOffset();

      grStatementInBody.getParent().getNode().removeChild(grStatementInBody.getNode());
    }

//    int endOffset = grWithStatement.getBody().getFirstChild().getTextRange().getEndOffset();

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "with (...)";
  }
}