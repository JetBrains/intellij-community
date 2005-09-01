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
package com.siyeh.ipp.concatenation;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceConcatenationWithStringBufferIntention extends Intention{
    public String getText(){
        return "Replace + with .append()";
    }

    public String getFamilyName(){
        return "Replace + with StringBuffer.append()";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ReplaceConcatenationWithStringBufferPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        PsiElement parent = exp.getParent();
        while(ConcatenationUtils.isConcatenation(parent)){
            exp = (PsiBinaryExpression) parent;
            assert exp != null;
            parent = exp.getParent();
        }
        final String text = exp.getText();
        @NonNls final StringBuffer expString = new StringBuffer(text.length() * 3);
        if(isPartOfStringBufferAppend(exp)){
            assert parent != null;
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) parent.getParent();
            assert methodCallExpression != null;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            final String qualifierText = qualifierExpression.getText();
            expString.append(qualifierText);
            turnExpressionIntoChainedAppends(exp, expString);
            final String newExpression = expString.toString();
            replaceExpression(newExpression, methodCallExpression);
        } else{
            final PsiManager manager = exp.getManager();
            final LanguageLevel languageLevel =
                    manager.getEffectiveLanguageLevel();
            if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
               languageLevel.equals(LanguageLevel.JDK_1_4)){
                expString.append("new StringBuffer()");
            } else{
                expString.append("new StringBuilder()");
            }
            turnExpressionIntoChainedAppends(exp, expString);
            expString.append(".toString()");
            final String newExpression = expString.toString();
            replaceExpression(newExpression, exp);
        }
    }

    private static boolean isPartOfStringBufferAppend(PsiExpression exp){
        PsiElement parent = exp.getParent();
        if(!(parent instanceof PsiExpressionList)){
            return false;
        }
        parent = parent.getParent();
        if(!(parent instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression methodCall =
                (PsiMethodCallExpression) parent;
        final PsiReferenceExpression methodExpression =
                methodCall.getMethodExpression();
        final PsiType type = methodExpression.getType();
        if(type == null){
            return false;
        }
        final String className = type.getCanonicalText();
        if(!"java.lang.StringBuffer".equals(className) &&
           !"java.lang.StringBuilder".equals(className)){
            return false;
        }
        @NonNls final String methodName = methodExpression.getReferenceName();
        return "append".equals(methodName);
    }

    private static void turnExpressionIntoChainedAppends(PsiExpression exp,
                                                         @NonNls StringBuffer expString){
        if(ConcatenationUtils.isConcatenation(exp)){
            final PsiBinaryExpression concat = (PsiBinaryExpression) exp;
            final PsiExpression lhs = concat.getLOperand();
            turnExpressionIntoChainedAppends(lhs, expString);
            final PsiExpression rhs = concat.getROperand();
            turnExpressionIntoChainedAppends(rhs, expString);
        } else{
            final PsiExpression strippedExpression =
                    ParenthesesUtils.stripParentheses(exp);
            expString.append(".append(" + strippedExpression.getText() + ')');
        }
    }
}
