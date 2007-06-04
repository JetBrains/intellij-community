package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurrounderByParametrizedClosure extends GroovyManyStatementsSurrounder {
  public String getTemplateDescription() {
    return "{param -> ... }";
  }

  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return "{ it -> \n" + getListElementsTemplateAsString(nodes) + "}";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrClosableBlock;

    GrClosableBlock closableBlock = (GrClosableBlock) element;
    GrParameterList it = closableBlock.getParameterList();
    int endOffset = it.getTextRange().getStartOffset();

    it.getParent().getNode().removeChild(it.getNode());

    return new TextRange(endOffset, endOffset);
  }
}