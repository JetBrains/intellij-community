package com.siyeh.ipp.shift;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ReplaceShiftWithMultiplyIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        if(element instanceof PsiBinaryExpression){
            final PsiBinaryExpression exp = (PsiBinaryExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if(tokenType.equals(JavaTokenType.LTLT)){
                operatorString = "*";
            } else{
                operatorString = "/";
            }
            return "Replace " + sign.getText() + " with " + operatorString;
        } else{
            final PsiAssignmentExpression exp =
                    (PsiAssignmentExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if(tokenType == JavaTokenType.LTLTEQ){
                assignString = "*=";
            } else{
                assignString = "/=";
            }
            return "Replace " + sign.getText() + " with " + assignString;
        }
    }

    public String getFamilyName(){
        return "Replace Shift with Multiply";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ShiftByLiteralPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        final PsiElement element = findMatchingElement(file, editor);
        if(element instanceof PsiBinaryExpression){
            final PsiBinaryExpression exp =
                    (PsiBinaryExpression) element;
            final PsiExpression lhs = exp.getLOperand();
            final PsiExpression rhs = exp.getROperand();
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if(tokenType.equals(JavaTokenType.LTLT)){
                operatorString = "*";
            } else{
                operatorString = "/";
            }
            final String expString =
            lhs.getText() + operatorString + ShiftUtils.getExpBase2(rhs);
            replaceExpression(project, expString, exp);
        } else{
            final PsiAssignmentExpression exp =
                    (PsiAssignmentExpression) element;
            final PsiExpression lhs = exp.getLExpression();
            final PsiExpression rhs = exp.getRExpression();
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if(tokenType.equals(JavaTokenType.LTLTEQ)){
                assignString = "*=";
            } else{
                assignString = "/=";
            }
            final String expString =
            lhs.getText() + assignString + ShiftUtils.getExpBase2(rhs);
            replaceExpression(project, expString, exp);
        }
    }
}
