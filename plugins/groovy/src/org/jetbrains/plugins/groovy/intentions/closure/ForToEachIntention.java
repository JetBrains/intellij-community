package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrBlockStatement;

public class ForToEachIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ForToEachPredicate();
  }

  public void processIntention(PsiElement element)
      throws IncorrectOperationException {
    final GrForStatement parentStatement =
        (GrForStatement) element;
    assert parentStatement != null;
    final GrForInClause clause = (GrForInClause) parentStatement.getClause();
    final GrVariable var = clause.getDeclaredVariables()[0];
    final GrStatement body = parentStatement.getBody();
    final String bodyText;
    if (body instanceof GrBlockStatement) {
      final String text = body.getText();
      bodyText = text.substring(1, text.length()-1);
    } else {
      bodyText = body.getText();

    }

    final GrExpression collection = clause.getIteratedExpression();
    @NonNls final String statement = collection.getText() + ".each{" + var.getText() + " -> " + bodyText + " }";
    replaceStatement(statement, parentStatement);
  }
}
