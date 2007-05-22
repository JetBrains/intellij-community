package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyExpressionSurrounder;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithBracketsSurrounder extends GroovyExpressionSurrounder {
  protected String getExpressionTemplateAsString(ASTNode node) {
    return "(" + node.getText() + ")";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    return new TextRange(element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset());
  }

  public String getTemplateDescription() {
    return "()";
  }
}
