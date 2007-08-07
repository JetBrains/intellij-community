package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class ExpandBooleanIntention extends Intention {


  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ExpandBooleanPredicate();
  }

  public void processIntention(@NotNull PsiElement element)
      throws IncorrectOperationException {
    final GrStatement containingStatement = (GrStatement) element;
    if (ExpandBooleanPredicate.isBooleanAssignment(containingStatement)) {

      final GrAssignmentExpression assignmentExpression =
          (GrAssignmentExpression) containingStatement;
      final GrExpression rhs = assignmentExpression.getRValue();
      assert rhs != null;
      final String rhsText = rhs.getText();
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      @NonNls final String statement =
          "if(" + rhsText + "){" + lhsText + " = true;}else{" +
              lhsText +
              " = false;}";
      replaceStatement(statement, containingStatement);
    } else if (ExpandBooleanPredicate.isBooleanReturn(containingStatement)) {
      final GrReturnStatement returnStatement =
          (GrReturnStatement) containingStatement;
      final GrExpression returnValue = returnStatement.getReturnValue();
      final String valueText = returnValue.getText();
      @NonNls final String statement =
          "if(" + valueText + "){return true;}else{return false;}";
      replaceStatement(statement, containingStatement);
    }
  }
}
