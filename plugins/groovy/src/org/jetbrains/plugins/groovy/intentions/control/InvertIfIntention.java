package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

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

    if (negatedCondition == null) {
      // Now check whether this is a simple expression
      condition = stripParenthesis(condition);
      String negatedExpressionText;
      if (condition instanceof GrCallExpression || condition instanceof GrReferenceExpression) {
        negatedExpressionText = "!" + condition.getText();
      }
      else {
        negatedExpressionText = "!(" + condition.getText() + ")";
      }
      negatedCondition = groovyPsiElementFactory.createExpressionFromText(negatedExpressionText, parentIf);
    }


    GrStatement thenBranch = parentIf.getThenBranch();
    String newIfText = "if (" + negatedCondition.getText() + ") " + generateElseBranchTextAndRemoveTailStatements(parentIf);

    boolean isThenEmpty = thenBranch == null ||
                          (thenBranch instanceof GrBlockStatement) && ((GrBlockStatement)thenBranch).getBlock().getStatements().length == 0;
    if (!isThenEmpty) {
      newIfText += " else " + thenBranch.getText();
    }

    GrIfStatement newIf = (GrIfStatement)groovyPsiElementFactory.createStatementFromText(newIfText, parentIf.getContext());
    parentIf.replace(CodeStyleManager.getInstance(project).reformat(newIf));
  }

  private static String generateElseBranchTextAndRemoveTailStatements(GrIfStatement ifStatement) {
    GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      return elseBranch.getText();
    }

    PsiElement parent = ifStatement.getParent();
    if (!(parent instanceof GrStatementOwner)) {
      return "{}";
    }

    String text = parent.getText();
    int start = ifStatement.getTextRange().getEndOffset() - parent.getTextRange().getStartOffset();
    PsiElement rbrace = parent instanceof GrCodeBlock ? ((GrCodeBlock)parent).getRBrace() : null;
    int end = rbrace != null ? rbrace.getStartOffsetInParent() : text.length();

    String lastStatements = text.substring(start, end);

    deleteLastStatements((GrStatementOwner)parent, ifStatement, rbrace);
    return "{\n" + lastStatements.trim() + "\n}";
  }

  private static void deleteLastStatements(GrStatementOwner statementOwner, GrIfStatement ifStatement, PsiElement rbrace) {
    PsiElement next = ifStatement.getNextSibling();
    if (next == null) return;

    if (rbrace == null) {
      statementOwner.getNode().removeRange(next.getNode(), null);
    }
    else {
      statementOwner.getNode().removeRange(next.getNode(), rbrace.getNode());
    }
  }

  @NotNull
  private static GrExpression stripParenthesis(GrExpression operand) {
    while (operand instanceof GrParenthesizedExpression) {
      GrExpression innerExpression = ((GrParenthesizedExpression)operand).getOperand();
      if (innerExpression == null) {
        break;
      }
      operand = innerExpression;
    }
    return operand;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement)) return false;
        if (((GrIfStatement)parent).getCondition() == null) return false;
        if (!"if".equals(element.getText())) return false;

        return true;
      }
    };
  }
}
