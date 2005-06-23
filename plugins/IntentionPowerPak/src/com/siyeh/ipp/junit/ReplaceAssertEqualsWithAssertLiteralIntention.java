package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ReplaceAssertEqualsWithAssertLiteralIntention
        extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){

        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = call.getArgumentList();
        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();
        final String assertString;
        if(args.length == 2){
            final String argText = args[0].getText();
            assertString = getAssertString(argText);
        } else{
            final String argText = args[1].getText();
            assertString = getAssertString(argText);
        }
        return "Replace 'assertEquals()' with '" + assertString + "()'";
    }

    public String getFamilyName(){
        return "Replace assertEquals with assertTrue, assertFalse, or assertNull";
    }

    public PsiElementPredicate getElementPredicate(){
        return new AssertEqualsWithLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) element;
        final PsiReferenceExpression expression = call.getMethodExpression();
        final PsiExpression qualifierExp = expression.getQualifierExpression();

        final String qualifier;
        if(qualifierExp == null){
            qualifier = "";
        } else{
            qualifier = qualifierExp.getText() + '.';
        }

        final PsiExpressionList argumentList = call.getArgumentList();
        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();
        final String callString;
        if(args.length == 2){
            final String argText = args[0].getText();
            final PsiExpression otherArg;
            if("true".equals(argText) ||
                    "false".equals(argText) ||
                    "null".equals(argText)){
                otherArg = args[1];
            } else{
                otherArg = args[0];
            }
            callString = qualifier + getAssertString(argText) + '(' +
                    otherArg.getText() + ')';
        } else{
            final String argText = args[1].getText();
            final PsiExpression otherArg;
            if("true".equals(argText) ||
                    "false".equals(argText) ||
                    "null".equals(argText)){
                otherArg = args[2];
            } else{
                otherArg = args[1];
            }
            callString = qualifier + getAssertString(argText) + '(' +
                    args[0].getText() + ", " + otherArg.getText() + ')';
        }
        replaceExpression(callString, call);
    }

    private static String getAssertString(String argText){
        if("true".equals(argText)){
            return "assertTrue";
        }
        if("false".equals(argText)){
            return "assertFalse";
        }
        return "assertNull";
    }
}
