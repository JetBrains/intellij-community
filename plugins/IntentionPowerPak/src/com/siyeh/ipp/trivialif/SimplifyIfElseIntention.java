package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import com.siyeh.ipp.psiutils.ConditionalUtils;

public class SimplifyIfElseIntention extends Intention{
    public String getText(){
        return "Simplify if-else";
    }

    public String getFamilyName(){
        return "Simplify If Else";
    }

    public PsiElementPredicate getElementPredicate(){
        return new SimplifyIfElsePredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        final PsiJavaToken token =
                (PsiJavaToken) findMatchingElement(file, editor);
        final PsiIfStatement statement = (PsiIfStatement) token.getParent();
        if(SimplifyIfElsePredicate.isSimplifiableAssignment(statement)){
            replaceSimplifiableAssignment(statement, project);
        } else if(SimplifyIfElsePredicate.isSimplifiableReturn(statement)){
            repaceSimplifiableReturn(statement, project);
        } else if(SimplifyIfElsePredicate.isSimplifiableImplicitReturn(statement)){
            replaceSimplifiableImplicitReturn(statement, project);
        } else if(SimplifyIfElsePredicate.isSimplifiableAssignmentNegated(statement)){
            replaceSimplifiableAssignmentNegated(statement, project);
        } else if(SimplifyIfElsePredicate.isSimplifiableReturnNegated(statement)){
            repaceSimplifiableReturnNegated(statement, project);
        } else if(SimplifyIfElsePredicate.isSimplifiableImplicitReturnNegated(statement)){
            replaceSimplifiableImplicitReturnNegated(statement, project);
        }
    }

    private static void replaceSimplifiableImplicitReturn(PsiIfStatement statement,
                                                          Project project)
            throws IncorrectOperationException{
        final PsiExpression condition = statement.getCondition();
        final String conditionText = condition.getText();
        final PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(statement, new Class[] {PsiWhiteSpace.class});
        final String newStatement = "return " + conditionText + ';';
        replaceStatement(project, newStatement, statement);
        nextStatement.delete();
    }

    private static void repaceSimplifiableReturn(PsiIfStatement statement,
                                                 Project project)
            throws IncorrectOperationException{
        final PsiExpression condition = statement.getCondition();
        final String conditionText = condition.getText();
        final String newStatement = "return " + conditionText + ';';
        replaceStatement(project, newStatement, statement);
    }

    private static void replaceSimplifiableAssignment(PsiIfStatement statement,
                                                      Project project)
            throws IncorrectOperationException{
        final PsiExpression condition = statement.getCondition();
        final String conditionText = condition.getText();
        final PsiStatement thenBranch = statement.getThenBranch();
        final PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression) assignmentStatement.getExpression();
        final PsiJavaToken operator = assignmentExpression.getOperationSign();
        final String operand = operator.getText();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final String lhsText = lhs.getText();
        replaceStatement(project, lhsText + operand + conditionText + ';',
                         statement);
    }

    private static void replaceSimplifiableImplicitReturnNegated(PsiIfStatement statement,
                                                                 Project project)
            throws IncorrectOperationException{
        final PsiExpression condition = statement.getCondition();

        final String conditionText =BoolUtils.getNegatedExpressionText(condition);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(statement, new Class[] {PsiWhiteSpace.class});
        final String newStatement = "return " + conditionText + ';';
        replaceStatement(project, newStatement, statement);
        nextStatement.delete();
    }

    private static void repaceSimplifiableReturnNegated(PsiIfStatement statement,
                                                        Project project)
            throws IncorrectOperationException{
        final PsiExpression condition = statement.getCondition();
        final String conditionText =
                BoolUtils.getNegatedExpressionText(condition);
        final String newStatement = "return " + conditionText + ';';
        replaceStatement(project, newStatement, statement);
    }

    private static void replaceSimplifiableAssignmentNegated(PsiIfStatement statement,
                                                             Project project)
            throws IncorrectOperationException{
        final PsiExpression condition = statement.getCondition();
        final String conditionText =
                BoolUtils.getNegatedExpressionText(condition);
        final PsiStatement thenBranch = statement.getThenBranch();
        final PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression) assignmentStatement.getExpression();
        final PsiJavaToken operator = assignmentExpression.getOperationSign();
        final String operand = operator.getText();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final String lhsText = lhs.getText();
        replaceStatement(project, lhsText + operand + conditionText + ';',
                         statement);
    }
}
