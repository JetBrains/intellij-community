package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
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
  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrTryCatchStatement;
    int endOffset = element.getTextRange().getEndOffset();
    GrTryCatchStatement tryCatchStatement = (GrTryCatchStatement) element;

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
    } else {
      GrOpenBlock block = tryCatchStatement.getTryBlock();
      if (block != null) {
        GrStatement[] statements = block.getStatements();
        if (statements.length > 0) {
          endOffset = statements[0].getTextRange().getStartOffset();
        }
      }
    }

    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "try";
  }
}
