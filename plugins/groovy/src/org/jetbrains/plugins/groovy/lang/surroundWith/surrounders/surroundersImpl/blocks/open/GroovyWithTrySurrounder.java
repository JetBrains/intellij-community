package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyWithTrySurrounder extends GroovyOpenBlockSurrounder {
  protected String getElementsTemplateAsString(ASTNode[] nodes) {
    return "try { \n" + getListElementsTemplateAsString(nodes) + "}";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrTryCatchStatement;
    GrCatchClause[] catchClauses = ((GrTryCatchStatement) element).getCatchClauses();
    assert catchClauses != null;

    int endOffset = element.getTextRange().getEndOffset();
    if (catchClauses.length > 0) {
      GrParameter parameter = catchClauses[0].getParameter();
      if (parameter == null) {
        endOffset = catchClauses[0].getTextRange().getEndOffset();
      } else {
        endOffset = parameter.getTextRange().getEndOffset();
      }
    }

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "try";
  }
}
