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
package com.siyeh.ipp.equality;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceEqualityWithSafeEqualsIntention extends Intention{
    public String getText(){
        return "Replace == with safe .equals()";
    }

    public String getFamilyName(){
        return "Replace Equality With Safe Equals";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ObjectEqualityPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiExpression strippedLhs =
                ParenthesesUtils.stripParentheses(lhs);
        final PsiExpression strippedRhs =
                ParenthesesUtils.stripParentheses(rhs);
        final String lhsText = strippedLhs.getText();
        final String rhsText = strippedRhs.getText();
        @NonNls final String expString;
        if(ParenthesesUtils.getPrecendence(strippedLhs) >
           ParenthesesUtils.METHOD_CALL_PRECEDENCE){
            expString = lhsText + "==null?" + rhsText + " == null:(" + lhsText +
                        ").equals(" + rhsText + ')';
        } else{
            expString = lhsText + "==null?" + rhsText + " == null:" + lhsText +
                        ".equals(" + rhsText + ')';
        }
        replaceExpression(expString, exp);
    }
}
