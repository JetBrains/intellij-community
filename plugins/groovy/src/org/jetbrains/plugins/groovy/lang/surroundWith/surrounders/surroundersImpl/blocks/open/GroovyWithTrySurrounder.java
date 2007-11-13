package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
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
  protected String getElementsTemplateAsString(PsiElement[] nodes) {
    return "try { \n" + getListElementsTemplateAsString(nodes) + "}";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrTryCatchStatement;
    int endOffset = element.getTextRange().getEndOffset();
    GrTryCatchStatement tryCatchStatement = (GrTryCatchStatement) element;

    GrFinallyClause finallyClause = tryCatchStatement.getFinallyClause();

    if (finallyClause != null) {
      GrOpenBlock block = finallyClause.getBody();
      assert block != null;
      GrStatement[] statements = block.getStatements();
      assert statements.length > 0;

      GrStatement grStatement = statements[0];
      assert grStatement != null;

      endOffset = grStatement.getTextRange().getStartOffset();
      grStatement.getParent().getNode().removeChild(grStatement.getNode());
    }

    GrCatchClause[] catchClauses = tryCatchStatement.getCatchClauses();

    if (catchClauses != null && catchClauses.length > 0) {
      GrParameter parameter = catchClauses[0].getParameter();
      if (parameter == null) {
        GrOpenBlock block = catchClauses[0].getBody();
        assert block != null;
        endOffset = block.getTextRange().getEndOffset();
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
