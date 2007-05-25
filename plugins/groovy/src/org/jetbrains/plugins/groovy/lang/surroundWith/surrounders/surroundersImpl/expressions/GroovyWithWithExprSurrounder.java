package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySingleElementSurrounder;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWithStatement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWithExprSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "with" + "(" + node.getText() + ") { \n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrWithStatement;

    GrWithStatement grWithStatement = (GrWithStatement) element;
    int endOffset = grWithStatement.getBody().getFirstChild().getTextRange().getEndOffset();

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "with (...)";
  }
}