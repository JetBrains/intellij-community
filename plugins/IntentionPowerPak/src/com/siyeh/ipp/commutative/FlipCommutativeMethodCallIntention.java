package com.siyeh.ipp.commutative;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class FlipCommutativeMethodCallIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        return "Flip ." + methodName + "()";
    }

    public String getFamilyName(){
        return "Flip Commutative Method Call";
    }

    public PsiElementPredicate getElementPredicate(){
        return new FlipCommutativeMethodCallPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{file.getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) findMatchingElement(file, editor);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        final PsiExpression target = methodExpression.getQualifierExpression();
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiExpression strippedTarget =
                ParenthesesUtils.stripParentheses(target);
        final PsiExpression strippedArg =
                ParenthesesUtils.stripParentheses(arg);
        final String callString;
        if(ParenthesesUtils.getPrecendence(strippedArg) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE){
            callString = '(' + strippedArg.getText() + ")." + methodName + '(' +
                    strippedTarget.getText() + ')';
        } else{
            callString = strippedArg.getText() + '.' + methodName + '(' +
                    strippedTarget.getText() + ')';
        }
        replaceExpression(project, callString, call);
    }
}
