/*
 * Copyright 2009 Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.VariableAccessUtils;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ReverseForLoopDirectionIntention extends Intention {

    @NotNull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ReverseForLoopDirectionPredicate();

    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiForStatement forStatement =
                (PsiForStatement)element.getParent();
        final PsiDeclarationStatement initialization =
                (PsiDeclarationStatement)forStatement.getInitialization();
        System.out.println("initialization: " + initialization);
        if (initialization == null) {
            return;
        }
        final PsiBinaryExpression condition =
                (PsiBinaryExpression)forStatement.getCondition();
        System.out.println("condition: " + condition);
        if (condition == null) {
            return;
        }
        final PsiVariable variable =
                (PsiVariable)initialization.getDeclaredElements()[0];
        final PsiExpression initializer = variable.getInitializer();
        System.out.println("initializer: " + initializer);
        if (initializer == null) {
            return;
        }
        final PsiExpression lhs = condition.getLOperand();
        final PsiExpression rhs = condition.getROperand();
        if (rhs == null) {
            return;
        }
        final PsiExpressionStatement update =
                (PsiExpressionStatement)forStatement.getUpdate();
        final PsiExpression updateExpression = update.getExpression();
        System.out.println("update: " + update);
        final String variableName = variable.getName();
        final StringBuilder updateText = new StringBuilder();
        if (updateExpression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)updateExpression;
            final IElementType tokenType =
                    prefixExpression.getOperationTokenType();
            if (JavaTokenType.PLUSPLUS == tokenType) {
                updateText.append("--");
            } else if (JavaTokenType.MINUSMINUS == tokenType) {
                updateText.append("++");
            } else {
                System.out.println("here");
                return;
            }
            updateText.append(variableName);
        } else if (updateExpression instanceof PsiPostfixExpression) {
            updateText.append(variableName);
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)updateExpression;
            final IElementType tokenType =
                    postfixExpression.getOperationTokenType();
            if (JavaTokenType.PLUSPLUS == tokenType) {
                updateText.append("--");
            } else if (JavaTokenType.MINUSMINUS == tokenType) {
                updateText.append("++");
            } else {
                return;
            }
        } else {
            return;
        }
        final Project project = element.getProject();
        final PsiElementFactory factory =
                JavaPsiFacade.getElementFactory(project);
        final PsiExpression newUpdate = factory.createExpressionFromText(
                updateText.toString(), element);
        System.out.println("newUpdate: " + newUpdate);
        updateExpression.replace(newUpdate);
        final PsiJavaToken sign = condition.getOperationSign();
        final String negatedSign = ComparisonUtils.getNegatedComparison(sign);
        final StringBuilder conditionText = new StringBuilder();
        final String initializerText = initializer.getText();
        if (VariableAccessUtils.evaluatesToVariable(lhs, variable)) {
            conditionText.append(variableName);
            conditionText.append(negatedSign);
            conditionText.append(initializerText);
            initializer.replace(rhs);
        } else if (VariableAccessUtils.evaluatesToVariable(rhs, variable)) {
            conditionText.append(initializerText);
            conditionText.append(negatedSign);
            conditionText.append(variableName);
            initializer.replace(lhs);
        } else {
            return;
        }
        final PsiExpression newCondition = factory.createExpressionFromText(
                conditionText.toString(), element);
        condition.replace(newCondition);
    }
}