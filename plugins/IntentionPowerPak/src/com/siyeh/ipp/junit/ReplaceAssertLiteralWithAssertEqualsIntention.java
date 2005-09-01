/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceAssertLiteralWithAssertEqualsIntention
        extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = call.getArgumentList();
        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        assert methodName != null;
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

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new AssertLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifierExp =
                methodExpression.getQualifierExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        assert methodName != null;
        final String literal = methodName.substring("assert".length())
                .toLowerCase();

        final String qualifier;
        if(qualifierExp == null){
            qualifier = "";
        } else{
            qualifier = qualifierExp.getText() + '.';
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        assert argumentList != null;
        final PsiExpression[] args = argumentList.getExpressions();

        @NonNls final String callString;
        if(args.length == 1){
            callString = qualifier + "assertEquals(" + literal + ", " +
                         args[0].getText() + ')';
        } else{
            callString =
                    qualifier + "assertEquals(" + args[0].getText() + ", " +
                    literal +
                    ", " + args[1].getText() +
                    ')';
        }
        replaceExpression(callString, call);
    }
}
