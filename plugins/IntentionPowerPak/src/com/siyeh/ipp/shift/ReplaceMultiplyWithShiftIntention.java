package com.siyeh.ipp.shift;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceMultiplyWithShiftIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        if(element instanceof PsiBinaryExpression){
            final PsiBinaryExpression exp = (PsiBinaryExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if(tokenType.equals(JavaTokenType.ASTERISK)){
                operatorString = "<<";
            } else{
                operatorString = ">>";
            }
            return "Replace " + sign.getText() + " with " + operatorString;
        } else{
            final PsiAssignmentExpression exp =
                    (PsiAssignmentExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if(tokenType.equals(JavaTokenType.ASTERISKEQ)){
                assignString = "<<=";
            } else{
                assignString = ">>=";
            }
            return "Replace " + sign.getText() + " with " + assignString;
        }
    }

    public String getFamilyName(){
        return "Replace Multiply with Shift";
    }

    public PsiElementPredicate getElementPredicate(){
        return new MultiplyByPowerOfTwoPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiElement matchingElement = findMatchingElement(file, editor);
        if(matchingElement instanceof PsiBinaryExpression){
            replaceMultiplyOrDivideWithShift(matchingElement, project);
        } else{
            replaceMultiplyOrDivideAssignWithShiftAssign(matchingElement,
                                                         project);
        }
    }

    private void replaceMultiplyOrDivideAssignWithShiftAssign(PsiElement matchingElement,
                                                              Project project)
            throws IncorrectOperationException{
        final PsiAssignmentExpression exp =
                (PsiAssignmentExpression) matchingElement;
        final PsiExpression lhs = exp.getLExpression();
        final PsiExpression rhs = exp.getRExpression();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String assignString;
        if(tokenType.equals(JavaTokenType.ASTERISKEQ)){
            assignString = "<<=";
        } else{
            assignString = ">>=";
        }
        final String expString =
                lhs.getText() + assignString + ShiftUtils.getLogBase2(rhs);
        replaceExpression(project, expString, exp);
    }

    private void replaceMultiplyOrDivideWithShift(PsiElement matchingElement,
                                                  Project project)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) matchingElement;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String operatorString;
        if(tokenType.equals(JavaTokenType.ASTERISK)){
            operatorString = "<<";
        } else{
            operatorString = ">>";
        }
        final String lhsText;
        if(ParenthesesUtils.getPrecendence(lhs) >
                   ParenthesesUtils.SHIFT_PRECEDENCE){
            lhsText = '(' + lhs.getText() + ')';
        } else{
            lhsText = lhs.getText();
        }
        String expString =
                lhsText + operatorString + ShiftUtils.getLogBase2(rhs);
        final PsiElement parent = exp.getParent();
        if(parent != null && parent instanceof PsiExpression){
            if(!(parent instanceof PsiParenthesizedExpression) &&
                       ParenthesesUtils.getPrecendence((PsiExpression) parent) <
                       ParenthesesUtils.SHIFT_PRECEDENCE){
                expString = '(' + expString + ')';
            }
        }
        replaceExpression(project, expString, exp);
    }
}
