package com.siyeh.ipp.junit;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;

public class CreateAssertIntention extends Intention{
    public String getText(){
        return "Create JUnit assertion";
    }

    public String getFamilyName(){
        return "Create JUnit assertion";
    }

    public PsiElementPredicate getElementPredicate(){
        return new CreateAssertPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiExpressionStatement statement =
                (PsiExpressionStatement) findMatchingElement(file, editor);
        final PsiExpression expression = statement.  getExpression();
        final String newExpression;
        if(BoolUtils.isNegation(expression)){
            newExpression = "assertFalse(" +
                    BoolUtils.getNegatedExpressionText(expression) + ");";
            replaceStatement(project,
                             newExpression,
                             statement);
        } else if(isNullComparison(expression)){
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiExpression comparedExpression;
            if(isNull(lhs))
            {
                comparedExpression = rhs;
            }
            else
            {
                comparedExpression = lhs;
            }
            newExpression = "assertNull(" +
                    comparedExpression.getText() + ");";
            replaceStatement(project,
                             newExpression,
                             statement);
        } else{
            newExpression = "assertTrue(" + expression.getText() + ");";
            replaceStatement(project,
                             newExpression,
                             statement);
        }
    }

    private boolean isNullComparison(PsiExpression expression){
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        if(sign == null)
        {
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        if(!JavaTokenType.EQEQ.equals(tokenType))
        {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if(isNull(lhs))
        {
            return true;
        }
        final PsiExpression Rhs = binaryExpression.getROperand();
        return isNull(Rhs);
    }

    private static boolean isNull(PsiExpression expression){
        if(!(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final String text = expression.getText();
        return "null".equals(text);
    }
}
