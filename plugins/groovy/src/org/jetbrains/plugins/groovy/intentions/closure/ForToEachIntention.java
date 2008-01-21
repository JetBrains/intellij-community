package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

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

    GrExpression collection = clause.getIteratedExpression();
    assert collection != null;
    collection = (GrExpression) collection.copy();
    @NonNls final String statement = "x.each{" + var.getText() + " -> " + bodyText + " }";
    final PsiManager mgr = parentStatement.getManager();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentStatement.getProject());
    final GrMethodCallExpression eachExpression =
        (GrMethodCallExpression) factory.createTopElementFromText(statement);
    ((GrReferenceExpression) eachExpression.getInvokedExpression()).getQualifierExpression().replaceWithExpression(collection, true);
    parentStatement.replaceWithStatement(eachExpression);
  }
}
