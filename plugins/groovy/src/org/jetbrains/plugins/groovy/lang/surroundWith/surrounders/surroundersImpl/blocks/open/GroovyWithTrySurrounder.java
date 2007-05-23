package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovyManyElementsSurrounder;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public abstract class GroovyWithTrySurrounder extends GroovyManyElementsSurrounder {
  protected String getExpressionTemplateAsString(ASTNode[] nodes) {
    StringBuffer result = new StringBuffer();
    result.append("try { \n");
    for (ASTNode node : nodes) {
      result.append(node.getText());
      result.append("\n");
    }
    result.append("}");
    return result.toString();
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

  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrStatement;
  }

  public String getTemplateDescription() {
    return "try";
  }
}
