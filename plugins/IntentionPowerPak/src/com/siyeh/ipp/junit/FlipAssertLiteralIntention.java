package com.siyeh.ipp.junit;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;

public class FlipAssertLiteralIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String fromMethodName = methodExpression.getReferenceName();
        final String toMethodName;
        if("assertTrue".equals(fromMethodName)){
            toMethodName = "assertFalse";
        } else{
            toMethodName = "assertTrue";
        }
        return "Replace " + fromMethodName + "() with " + toMethodName + "()";
    }

    public String getFamilyName(){
        return "Flip Assert Literal";
    }

    public PsiElementPredicate getElementPredicate(){
        return new AssertTrueOrFalsePredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{file.getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) findMatchingElement(file, editor);

        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String fromMethodName = methodExpression.getReferenceName();
        final String toMethodName;
        if("assertTrue".equals(fromMethodName)){
            toMethodName = "assertFalse";
        } else{
            toMethodName = "assertTrue";
        }
        final PsiExpression qualifierExp =
                methodExpression.getQualifierExpression();

        final String qualifier;
        if(qualifierExp == null){
            qualifier = "";
        } else{
            qualifier = qualifierExp.getText() + '.';
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final String callString;
        if(args.length == 1){
            final PsiExpression arg = args[0];
            callString = qualifier + toMethodName + '(' +
                    BoolUtils.getNegatedExpressionText(arg) + ')';
        } else{
            final PsiExpression arg = args[1];
            callString =
            qualifier + toMethodName + '(' + args[0].getText() + ',' +
                    BoolUtils.getNegatedExpressionText(arg) +
                    ')';
        }
        replaceExpression(project, callString, call);
    }
}
