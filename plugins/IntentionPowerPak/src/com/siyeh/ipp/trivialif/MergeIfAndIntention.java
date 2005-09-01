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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class MergeIfAndIntention extends Intention{
    public String getText(){
        return "Merge 'if's";
    }

    public String getFamilyName(){
        return "Merge Nested Ifs To ANDed Condition";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new MergeIfAndPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token =
                (PsiJavaToken) element;
        final PsiIfStatement parentStatement =
                (PsiIfStatement) token.getParent();
        assert parentStatement != null;
        final PsiStatement parentThenBranch = parentStatement.getThenBranch();
        final PsiIfStatement childStatement =
                (PsiIfStatement) ConditionalUtils.stripBraces(parentThenBranch);

        final PsiExpression childCondition = childStatement.getCondition();
        final String childConditionText;
        if(ParenthesesUtils.getPrecendence(childCondition)
           > ParenthesesUtils.AND_PRECEDENCE){
            childConditionText = '(' + childCondition.getText() + ')';
        } else{
            childConditionText = childCondition.getText();
        }

        final PsiExpression parentCondition = parentStatement.getCondition();
        final String parentConditionText;
        if(ParenthesesUtils.getPrecendence(parentCondition)
           > ParenthesesUtils.AND_PRECEDENCE){
            parentConditionText = '(' + parentCondition.getText() + ')';
        } else{
            parentConditionText = parentCondition.getText();
        }

        final PsiStatement childThenBranch = childStatement.getThenBranch();
        @NonNls final String statement =
                "if(" + parentConditionText + "&&" + childConditionText + ')' +
                childThenBranch.getText();
        replaceStatement(statement, parentStatement);
    }
}