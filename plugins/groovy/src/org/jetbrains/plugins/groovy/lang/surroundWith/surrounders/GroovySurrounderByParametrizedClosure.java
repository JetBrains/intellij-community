package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurrounderByParametrizedClosure extends GroovyManyStatementsSurrounder {
  public String getTemplateDescription() {
    return "{it -> ... }";
  }

  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return "{ it -> \n" + getListElementsTemplateAsString(nodes) + "}";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrClosableBlock;

    int endOffset = element.getTextRange().getEndOffset();
    return new TextRange(endOffset, endOffset);
  }

  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrStatement;
  }
}