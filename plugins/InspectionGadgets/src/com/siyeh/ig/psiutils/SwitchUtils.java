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
package com.siyeh.ig.psiutils;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwitchUtils{

    private SwitchUtils(){}

    public static int calculateBranchCount(
            @NotNull PsiSwitchStatement statement){
        final PsiCodeBlock body = statement.getBody();
        int branches = 0;
        if (body == null) {
            return branches;
        }
        final PsiStatement[] statements = body.getStatements();
        for(final PsiStatement child : statements){
            if(child instanceof PsiSwitchLabelStatement){
                branches++;
            }
        }
        return branches;
    }

    @Nullable
    public static PsiExpression getSwitchExpression(PsiIfStatement statement,
                                                    int minimumBranches){
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
        int branchCount = 0;
        while(true){
            branchCount++;
            final PsiExpression caseCondition = statement.getCondition();
            if (!canBeMadeIntoCase(caseCondition, possibleSwitchExpression,
                    languageLevel)) {
                break;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if(!(elseBranch instanceof PsiIfStatement)){
                if (elseBranch != null) {
                    branchCount++;
                }
                if (branchCount < minimumBranches) {
                    return null;
                }
                return possibleSwitchExpression;
            }
            statement = (PsiIfStatement) elseBranch;
        }
        return null;
    }

    private static boolean canBeMadeIntoCase(
            PsiExpression expression, PsiExpression switchExpression,
            LanguageLevel languageLevel) {
        while(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            expression = parenthesizedExpression.getExpression();
        }
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiExpression stringCaseExpression =
                    determinePossibleStringSwitchExpression(expression);
            if (EquivalenceChecker.expressionsAreEquivalent(switchExpression,
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
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if(operation.equals(JavaTokenType.OROR)){
            return canBeMadeIntoCase(lhs, switchExpression, languageLevel) &&
                    canBeMadeIntoCase(rhs, switchExpression, languageLevel);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            return (canBeCaseLabel(lhs, languageLevel) &&
                    EquivalenceChecker.expressionsAreEquivalent(
                            switchExpression, rhs))
                    ||
                    (canBeCaseLabel(rhs, languageLevel) &&
                            EquivalenceChecker.expressionsAreEquivalent(
                                    switchExpression, lhs));
        } else {
            return false;
        }
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
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
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
            if (canBeCaseLabel(lhs, languageLevel) &&
                    canBeSwitchExpression(rhs, languageLevel)) {
                return rhs;
            } else if (canBeCaseLabel(rhs, languageLevel) &&
                    canBeSwitchExpression(lhs, languageLevel)) {
                return lhs;
            }
        }
        return null;
    }

    private static boolean canBeSwitchExpression(PsiExpression expression, LanguageLevel languageLevel) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        if (PsiType.CHAR.equals(type) || PsiType.BYTE.equals(type) ||
                PsiType.SHORT.equals(type) || PsiType.INT.equals(type)) {
            return true;
        } else if (type instanceof PsiClassType) {
            if (type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER) ||
                    type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
                    type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
                    type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
                return true;
            }
            if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
                final PsiClassType classType = (PsiClassType)type;
                final PsiClass aClass = classType.resolve();
                if (aClass != null && aClass.isEnum()) {
                    return true;
                }
            }
            if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7) &&
                    type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                return true;
            }
        }
        return false;
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

    private static boolean canBeCaseLabel(PsiExpression expression,
                                          LanguageLevel languageLevel){
        if(expression == null){
            return false;
        }
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) &&
                expression instanceof PsiReferenceExpression){
            final PsiElement referent = ((PsiReference) expression).resolve();
            if(referent instanceof PsiEnumConstant){
                return true;
            }
        }
        final PsiType type = expression.getType();
        return type != null &&
                (PsiType.INT.equals(type) || PsiType.CHAR.equals(type) ||
                PsiType.SHORT.equals(type) || PsiType.BYTE.equals(type)) &&
                PsiUtil.isConstantExpression(expression);
    }

    public static String findUniqueLabelName(PsiStatement statement,
                                             @NonNls String baseName){
        PsiElement ancestor = statement;
        while(ancestor.getParent() != null){
            if(ancestor instanceof PsiMethod
                    || ancestor instanceof PsiClass
                    || ancestor instanceof PsiFile){
                break;
            }
            ancestor = ancestor.getParent();
        }
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

    private static class LabelSearchVisitor
            extends JavaRecursiveElementWalkingVisitor {

        private final String m_labelName;
        private boolean m_used = false;

        LabelSearchVisitor(String name){
            m_labelName = name;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (m_used) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitLabeledStatement(PsiLabeledStatement statement){
            final PsiIdentifier labelIdentifier =
                    statement.getLabelIdentifier();
            final String labelText = labelIdentifier.getText();
            if(labelText.equals(m_labelName)){
                m_used = true;
            }
        }

        public boolean isUsed(){
            return m_used;
        }
    }
}
