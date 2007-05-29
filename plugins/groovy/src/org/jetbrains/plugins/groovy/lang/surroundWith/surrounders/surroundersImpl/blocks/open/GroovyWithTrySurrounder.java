package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
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
    int endOffset = element.getTextRange().getEndOffset();
    GrTryCatchStatement tryCatchStatement = (GrTryCatchStatement) element;

    GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();

    if (finallyClause != null) {
      GrOpenBlock grOpenBlock = finallyClause.getBody();
      assert grOpenBlock != null;
      GrStatement[] grStatements = grOpenBlock.getStatements();
      assert grStatements.length > 0;

      GrStatement grStatement = grStatements[0];
      assert grStatement != null;

      endOffset = grStatement.getTextRange().getStartOffset();
      grStatement.getParent().getNode().removeChild(grStatement.getNode());
    }

    GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();

    if (catchClauses != null && catchClauses.length > 0) {
      GrParameter parameter = catchClauses[0].getParameter();
      if (parameter == null) {
        GrOpenBlock grOpenBlock = catchClauses[0].getBody();
        assert grOpenBlock != null;
        endOffset = grOpenBlock.getTextRange().getEndOffset();
      } else {
        endOffset = parameter.getTextRange().getStartOffset();
        parameter.getParent().getNode().removeChild(parameter.getNode());
      }
    }

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "try";
  }
}
