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
import com.siyeh.ipp.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class FlipAssertLiteralIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String fromMethodName = methodExpression.getReferenceName();
        @NonNls final String toMethodName;
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

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new AssertTrueOrFalsePredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        @NonNls final String fromMethodName = methodExpression.getReferenceName();
        @NonNls final String toMethodName;
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
        assert argumentList != null;
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
        replaceExpression(callString, call);
    }
}
