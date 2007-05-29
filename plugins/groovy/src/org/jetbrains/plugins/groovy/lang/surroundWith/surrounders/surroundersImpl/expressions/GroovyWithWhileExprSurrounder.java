package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWhileExprSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "while " + "(" + node.getText() + ") { 4 \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrWhileStatement;

    GrWhileStatement grWhileStatement = (GrWhileStatement) element;
    GrStatement grStatement = grWhileStatement.getBody();
    int endOffset = grStatement.getTextRange().getEndOffset();

    grStatement.getParent().getNode().removeChild(grStatement.getNode());
    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "while (...) {}";
  }
}
