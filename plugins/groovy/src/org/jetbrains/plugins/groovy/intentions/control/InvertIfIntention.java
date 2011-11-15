package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

/**
 * @author Niels Harremoes
 */
public class InvertIfIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    if (!"if".equals(element.getText()) || !(parent instanceof GrIfStatement)) {
      throw new IncorrectOperationException("Not invoked on an if");
    }
    GrIfStatement parentIf = (GrIfStatement)parent;
    GroovyPsiElementFactory groovyPsiElementFactory = GroovyPsiElementFactory.getInstance(project);

    GrStatement thenBranch = parentIf.getThenBranch();
    GrStatement elseBranch = parentIf.getElseBranch();

    GrExpression condition = parentIf.getCondition();
    if (condition == null) {
      throw new IncorrectOperationException("Invoked on an if with empty condition");
    }

    GrExpression negatedCondition = null;
    if (condition instanceof GrUnaryExpression) {
      GrUnaryExpression unaryCondition = (GrUnaryExpression)condition;
      if ("!".equals(unaryCondition.getOperationToken().getText())) {
        negatedCondition = stripParenthesis(unaryCondition.getOperand());
      }
    }

    if (negatedCondition==null) {
      // Now check whether this is a simple expression
      condition = stripParenthesis(condition);
      String negatedExpressionText;
      if (condition instanceof GrCallExpression || condition instanceof GrReferenceExpression) {
        negatedExpressionText = "!" + condition.getText();
      } else {
        negatedExpressionText = "!(" + condition.getText() + ")";
      }
      negatedCondition = groovyPsiElementFactory.createExpressionFromText(negatedExpressionText, parentIf);
    }

    GrIfStatement newIf = (GrIfStatement)groovyPsiElementFactory.createStatementFromText(
      "if (" + negatedCondition.getText() + ") " +
      (elseBranch != null ? elseBranch.getText() : "{}") + " else " +
      (thenBranch != null ? thenBranch.getText() : "{}"), parentIf.getContext()
    );

    parentIf.replace(newIf);
  }

  private static GrExpression stripParenthesis(GrExpression operand) {
    while (operand instanceof GrParenthesizedExpression) {
      operand = ((GrParenthesizedExpression) operand).getOperand();
    }
    return operand;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {

        if (!"if".equals(element.getText())) {
          return false;
        }
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement)) {
          return false;
        }

        if (((GrIfStatement)parent).getCondition() == null) {
          return false;
        }
        return true;
      }
    };
  }
}
