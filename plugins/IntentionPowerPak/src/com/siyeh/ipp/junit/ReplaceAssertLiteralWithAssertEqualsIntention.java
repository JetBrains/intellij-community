package com.siyeh.ipp.junit;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ReplaceAssertLiteralWithAssertEqualsIntention
        extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        final String literal = methodName.substring("assert".length())
                        .toLowerCase();

        final String messageText;
        if(args.length == 1){
            messageText = "";
        } else{
            messageText = "..., ";
        }
        return "Replace " + methodName + "() with assertEquals(" + messageText +
                literal + ", ...)";
    }

    public String getFamilyName(){
        return "Replace assertTrue, assertFalse, or assertNull with assertEquals";
    }

    public PsiElementPredicate getElementPredicate(){
        return new AssertLiteralPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) findMatchingElement(file, editor);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifierExp =
                methodExpression.getQualifierExpression();
        final String methodName = methodExpression.getReferenceName();
        final String literal = methodName.substring("assert".length())
                        .toLowerCase();

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
            callString = qualifier + "assertEquals(" + literal + ", " +
                    args[0].getText() + ')';
        } else{
            callString =
            qualifier + "assertEquals(" + args[0].getText() + ", " + literal +
                    ", " + args[1].getText() +
                    ')';
        }
        replaceExpression(project, callString, call);
    }
}
