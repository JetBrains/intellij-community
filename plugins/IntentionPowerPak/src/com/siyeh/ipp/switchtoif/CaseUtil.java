/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.switchtoif;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class CaseUtil{

    private CaseUtil(){
        super();
    }

    private static boolean canBeCaseLabel(PsiExpression expression,
                                          LanguageLevel languageLevel){
        if(expression == null){
            return false;
        }
        if (languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0
                && expression instanceof PsiReferenceExpression){
            final PsiElement referent = ((PsiReference) expression).resolve();
            if(referent instanceof PsiEnumConstant){
                return true;
            }
        }
        final PsiType type = expression.getType();
        return type != null &&
                (type.equals(PsiType.INT) ||
                        type.equals(PsiType.CHAR) ||
                        type.equals(PsiType.LONG) ||
                        type.equals(PsiType.SHORT)) &&
                PsiUtil.isConstantExpression(expression);
    }

    public static boolean isUsedByStatementList(PsiLocalVariable variable,
                                                List<PsiElement> elements){
        for(PsiElement element : elements){
            if(isUsedByStatement(variable, element)){
                return true;
            }
        }
        return false;
    }

    private static boolean isUsedByStatement(PsiLocalVariable variable,
                                             PsiElement statement){
        final LocalVariableUsageVisitor visitor =
                new LocalVariableUsageVisitor(variable);
        statement.accept(visitor);
        return visitor.isUsed();
    }

    public static String findUniqueLabelName(PsiStatement statement,
                                             @NonNls String baseName){
        final PsiElement ancestor =
                PsiTreeUtil.getParentOfType(statement, PsiMember.class);
        if(!checkForLabel(baseName, ancestor)){
            return baseName;
        }
        int val = 1;
        while(true){
            final String name = baseName + val;
            if(!checkForLabel(name, ancestor)){
                return name;
            }
            val++;
        }
    }

    private static boolean checkForLabel(String name, PsiElement ancestor){
        final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
        ancestor.accept(visitor);
        return visitor.isUsed();
    }

    @Nullable
    public static PsiExpression getSwitchExpression(PsiIfStatement statement){
        final PsiExpression condition = statement.getCondition();
        final LanguageLevel languageLevel =
                PsiUtil.getLanguageLevel(statement);
        final PsiExpression possibleSwitchExpression =
                determinePossibleSwitchExpressions(condition, languageLevel);
        if(possibleSwitchExpression == null){
            return null;
        }
        if (SideEffectChecker.mayHaveSideEffects(possibleSwitchExpression)) {
            return null;
        }
        while(true){
            final PsiExpression caseCondition = statement.getCondition();
            if (!canBeMadeIntoCase(caseCondition, possibleSwitchExpression,
                    languageLevel)) {
                break;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if(!(elseBranch instanceof PsiIfStatement)){
                return possibleSwitchExpression;
            }
            statement = (PsiIfStatement) elseBranch;
        }
        return null;
    }

    private static PsiExpression determinePossibleSwitchExpressions(
            PsiExpression expression, LanguageLevel languageLevel){
        while(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            expression = parenthesizedExpression.getExpression();
        }
        if (expression == null) {
            return null;
        }
        if (languageLevel.compareTo(LanguageLevel.JDK_1_7) >= 0) {
            final PsiExpression jdk17Expression =
                    determinePossibleStringSwitchExpression(expression);
            if (jdk17Expression != null) {
                return jdk17Expression;
            }
        }
        if (!(expression instanceof PsiBinaryExpression)){
            return null;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType operation = sign.getTokenType();
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if(operation.equals(JavaTokenType.OROR)){
            return determinePossibleSwitchExpressions(lhs, languageLevel);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            if(canBeCaseLabel(lhs, languageLevel)){
                return rhs;
            } else if (canBeCaseLabel(rhs, languageLevel)){
                return lhs;
            }
        }
        return null;
    }

    private static PsiExpression determinePossibleStringSwitchExpression(
            PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return null;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) expression;
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        @NonNls final String referenceName =
                methodExpression.getReferenceName();
        if (!"equals".equals(referenceName)) {
            return null;
        }
        final PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        final PsiType type = qualifierExpression.getType();
        if (type == null || !type.equalsToText("java.lang.String")) {
            return null;
        }
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
            return null;
        }
        final PsiExpression argument = arguments[0];
        final PsiType argumentType = argument.getType();
        if (argumentType == null ||
                !argumentType.equalsToText("java.lang.String")) {
            return null;
        }
        if (PsiUtil.isConstantExpression(qualifierExpression)) {
            return argument;
        } else if (PsiUtil.isConstantExpression(argument)) {
            return qualifierExpression;
        }
        return null;
    }

    private static boolean canBeMadeIntoCase(
            PsiExpression expression, PsiExpression caseExpression,
            LanguageLevel languageLevel) {
        while(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            expression = parenthesizedExpression.getExpression();
        }
        if (languageLevel.compareTo(LanguageLevel.JDK_1_7) >=0 ) {
            final PsiExpression stringCaseExpression =
                    determinePossibleStringSwitchExpression(expression);
            if (EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                    stringCaseExpression)) {
                return true;
            }
        }
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType operation = sign.getTokenType();
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if(operation.equals(JavaTokenType.OROR)){
            return canBeMadeIntoCase(lOperand, caseExpression, languageLevel) &&
                    canBeMadeIntoCase(rhs, caseExpression, languageLevel);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            return (canBeCaseLabel(lOperand, languageLevel) &&
                    EquivalenceChecker.expressionsAreEquivalent(
                            caseExpression, rhs))
                    ||
                    (canBeCaseLabel(rhs, languageLevel) &&
                            EquivalenceChecker.expressionsAreEquivalent(
                                    caseExpression, lOperand));
        } else {
            return false;
        }
    }
}