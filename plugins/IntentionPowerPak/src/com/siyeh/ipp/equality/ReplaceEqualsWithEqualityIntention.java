package com.siyeh.ipp.equality;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceEqualsWithEqualityIntention extends Intention{
    public String getText(){
        return "Replace .equals() with ==";
    }

    public String getFamilyName(){
        return "Replace Equals With Equality";
    }

    public PsiElementPredicate getElementPredicate(){
        return new EqualsPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{file.getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) findMatchingElement(file, editor);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression target = methodExpression.getQualifierExpression();
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiExpression strippedTarget =
                ParenthesesUtils.stripParentheses(target);
        final PsiExpression strippedArg =
                ParenthesesUtils.stripParentheses(arg);

        final String strippedArgText;
        if(ParenthesesUtils.getPrecendence(strippedArg) >
                ParenthesesUtils.EQUALITY_PRECEDENCE){
            strippedArgText = '(' + strippedArg.getText() + ')';
        } else{
            strippedArgText = strippedArg.getText();
        }
        final String strippedTargetText;
        if(ParenthesesUtils.getPrecendence(strippedTarget) >
                ParenthesesUtils.EQUALITY_PRECEDENCE){
            strippedTargetText = '(' + strippedTarget.getText() + ')';
        } else{
            strippedTargetText = strippedTarget.getText();
        }

        replaceExpression(project, strippedTargetText + "==" + strippedArgText,
                          call);
    }
}
