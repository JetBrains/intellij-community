package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWithStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

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

    GrWithStatement withStatement = (GrWithStatement) element;

    GrOpenBlock block = withStatement.getBody();

    GrStatement statementInBody = block.getStatements()[0];
    int endOffset = statementInBody.getTextRange().getEndOffset();

    statementInBody.getParent().getNode().removeChild(statementInBody.getNode());

//    int endOffset = withStatement.getBody().getFirstChild().getTextRange().getEndOffset();

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "with (...)";
  }
}