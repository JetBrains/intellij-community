/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquivalenceChecker{

    private EquivalenceChecker(){
        super();
    }

    public static boolean modifierListsAreEquivalent(
            @Nullable PsiModifierList list1, @Nullable PsiModifierList list2) {
        if (list1 == null) {
            return list2 == null;
        } else if (list2 == null) {
            return false;
        }
        final PsiAnnotation[] annotations = list1.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            final String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName == null) {
                return false;
            }
            if (list2.findAnnotation(qualifiedName) == null) {
                return false;
            }
        }
        if (list1.hasModifierProperty(PsiModifier.ABSTRACT) &&
                !list2.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.FINAL) &&
                !list2.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.NATIVE) &&
                !list2.hasModifierProperty(PsiModifier.NATIVE)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                !list2.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.PRIVATE) &&
                !list2.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.PROTECTED) &&
                !list2.hasModifierProperty(PsiModifier.PROTECTED)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.PUBLIC) &&
                !list2.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.STATIC) &&
                !list2.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.STRICTFP) &&
                !list2.hasModifierProperty(PsiModifier.STRICTFP)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.SYNCHRONIZED) &&
                !list2.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            return false;
        }
        if (list1.hasModifierProperty(PsiModifier.TRANSIENT) &&
                !list2.hasModifierProperty(PsiModifier.TRANSIENT)) {
            return false;
        }
        return !(list1.hasModifierProperty(PsiModifier.VOLATILE) &&
                !list2.hasModifierProperty(PsiModifier.VOLATILE));
    }

    public static boolean statementsAreEquivalent(
            @Nullable PsiStatement statement1,
            @Nullable PsiStatement statement2) {
        if(statement1 == null && statement2 == null){
            return true;
        }
        if(statement1 == null || statement2 == null){
            return false;
        }
        if(statement1.getClass() != statement2.getClass()){
            return false;
        }
        if(statement1 instanceof PsiAssertStatement){
            final PsiAssertStatement assertStatement1 =
                    (PsiAssertStatement)statement1;
            final PsiAssertStatement assertStatement2 =
                    (PsiAssertStatement)statement2;
            return assertStatementsAreEquivalent(assertStatement1,
                    assertStatement2);
        }
        if(statement1 instanceof PsiBlockStatement){
            final PsiBlockStatement blockStatement1 =
                    (PsiBlockStatement)statement1;
            final PsiBlockStatement blockStatement2 =
                    (PsiBlockStatement)statement2;
            return blockStatementsAreEquivalent(blockStatement1,
                    blockStatement2);
        }
        if(statement1 instanceof PsiBreakStatement){
            final PsiBreakStatement breakStatement1 =
                    (PsiBreakStatement)statement1;
            final PsiBreakStatement breakStatement2 =
                    (PsiBreakStatement)statement2;
            return breakStatementsAreEquivalent(breakStatement1,
                    breakStatement2);
        }
        if(statement1 instanceof PsiContinueStatement){
            final PsiContinueStatement continueStatement1 =
                    (PsiContinueStatement)statement1;
            final PsiContinueStatement continueStatement2 =
                    (PsiContinueStatement)statement2;
            return continueStatementsAreEquivalent(continueStatement1,
                    continueStatement2);
        }
        if(statement1 instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declarationStatement1 =
                    (PsiDeclarationStatement)statement1;
            final PsiDeclarationStatement declarationStatement2 =
                    (PsiDeclarationStatement)statement2;
            return declarationStatementsAreEquivalent(declarationStatement1,
                    declarationStatement2);
        }
        if(statement1 instanceof PsiDoWhileStatement){
            final PsiDoWhileStatement doWhileStatement1 =
                    (PsiDoWhileStatement)statement1;
            final PsiDoWhileStatement doWhileStatement2 =
                    (PsiDoWhileStatement)statement2;
            return doWhileStatementsAreEquivalent(
                    doWhileStatement1, doWhileStatement2);
        }
        if(statement1 instanceof PsiEmptyStatement){
            return true;
        }
        if(statement1 instanceof PsiExpressionListStatement){
            final PsiExpressionListStatement expressionListStatement1 =
                    (PsiExpressionListStatement)statement1;
            final PsiExpressionListStatement expressionListStatement2 =
                    (PsiExpressionListStatement)statement2;
            return expressionListStatementsAreEquivalent(
                    expressionListStatement1,
                    expressionListStatement2);
        }
        if(statement1 instanceof PsiExpressionStatement){
            final PsiExpressionStatement expressionStatement1 =
                    (PsiExpressionStatement)statement1;
            final PsiExpressionStatement expressionStatement2 =
                    (PsiExpressionStatement)statement2;
            return expressionStatementsAreEquivalent(
                    expressionStatement1,
                    expressionStatement2);
        }
        if(statement1 instanceof PsiForStatement){
            final PsiForStatement forStatement1 =
                    (PsiForStatement)statement1;
            final PsiForStatement forStatement2 =
                    (PsiForStatement)statement2;
            return forStatementsAreEquivalent(forStatement1, forStatement2);
        }
        if(statement1 instanceof PsiForeachStatement){
            final PsiForeachStatement forEachStatement1 =
                    (PsiForeachStatement)statement1;
            final PsiForeachStatement forEachStatement2 =
                    (PsiForeachStatement)statement2;
            return forEachStatementsAreEquivalent(forEachStatement1,
                    forEachStatement2);
        }
        if(statement1 instanceof PsiIfStatement){
            return ifStatementsAreEquivalent(
                    (PsiIfStatement) statement1,
                    (PsiIfStatement) statement2);
        }
        if(statement1 instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement1 =
                    (PsiLabeledStatement)statement1;
            final PsiLabeledStatement labeledStatement2 =
                    (PsiLabeledStatement)statement2;
            return labeledStatementsAreEquivalent(labeledStatement1,
                    labeledStatement2);
        }
        if(statement1 instanceof PsiReturnStatement){
            final PsiReturnStatement returnStatement1 =
                    (PsiReturnStatement)statement1;
            final PsiReturnStatement returnStatement2 =
                    (PsiReturnStatement)statement2;
            return returnStatementsAreEquivalent(returnStatement1,
                    returnStatement2);
        }
        if(statement1 instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement1 =
                    (PsiSwitchStatement)statement1;
            final PsiSwitchStatement switchStatement2 =
                    (PsiSwitchStatement)statement2;
            return switchStatementsAreEquivalent(switchStatement1,
                    switchStatement2);
        }
        if(statement1 instanceof PsiSwitchLabelStatement){
            final PsiSwitchLabelStatement switchLabelStatement1 =
                    (PsiSwitchLabelStatement)statement1;
            final PsiSwitchLabelStatement switchLabelStatement2 =
                    (PsiSwitchLabelStatement)statement2;
            return switchLabelStatementsAreEquivalent(switchLabelStatement1,
                    switchLabelStatement2);
        }
        if(statement1 instanceof PsiSynchronizedStatement){
            final PsiSynchronizedStatement synchronizedStatement1 =
                    (PsiSynchronizedStatement)statement1;
            final PsiSynchronizedStatement synchronizedStatement2 =
                    (PsiSynchronizedStatement)statement2;
            return synchronizedStatementsAreEquivalent(
                    synchronizedStatement1, synchronizedStatement2);
        }
        if(statement1 instanceof PsiThrowStatement){
            final PsiThrowStatement throwStatement1 =
                    (PsiThrowStatement)statement1;
            final PsiThrowStatement throwStatement2 =
                    (PsiThrowStatement)statement2;
            return throwStatementsAreEquivalent(throwStatement1,
                    throwStatement2);
        }
        if(statement1 instanceof PsiTryStatement){
            final PsiTryStatement tryStatement1 =
                    (PsiTryStatement)statement1;
            final PsiTryStatement tryStatement2 =
                    (PsiTryStatement)statement2;
            return tryStatementsAreEquivalent(tryStatement1,
                    tryStatement2);
        }
        if(statement1 instanceof PsiWhileStatement){
            final PsiWhileStatement whileStatement1 =
                    (PsiWhileStatement)statement1;
            final PsiWhileStatement whileStatement2 =
                    (PsiWhileStatement)statement2;
            return whileStatementsAreEquivalent(whileStatement1,
                    whileStatement2);
        }
        final String text1 = statement1.getText();
        final String text2 = statement2.getText();
        return text1.equals(text2);
    }

    private static boolean declarationStatementsAreEquivalent(
            @NotNull PsiDeclarationStatement statement1,
            @NotNull  PsiDeclarationStatement statement2){
        final PsiElement[] elements1 = statement1.getDeclaredElements();
        final List<PsiLocalVariable> vars1 =
                new ArrayList<PsiLocalVariable>(elements1.length);
        for(PsiElement anElement : elements1){
            if(anElement instanceof PsiLocalVariable){
                vars1.add((PsiLocalVariable) anElement);
            }
        }
        final PsiElement[] elements2 = statement2.getDeclaredElements();
        final List<PsiLocalVariable> vars2 =
                new ArrayList<PsiLocalVariable>(elements2.length);
        for(PsiElement anElement : elements2){
            if(anElement instanceof PsiLocalVariable){
                vars2.add((PsiLocalVariable) anElement);
            }
        }
        final int size = vars1.size();
        if(size != vars2.size()){
            return false;
        }
        for(int i = 0; i < size; i++){
            final PsiLocalVariable var1 = vars1.get(i);
            final PsiLocalVariable var2 = vars2.get(i);
            if(!localVariableAreEquivalent(var1, var2)){
                return false;
            }
        }
        return true;
    }

    private static boolean localVariableAreEquivalent(
            @NotNull  PsiLocalVariable var1, @NotNull PsiLocalVariable var2) {
        final PsiType type1 = var1.getType();
        final PsiType type2 = var2.getType();
        if(!typesAreEquivalent(type1, type2)){
            return false;
        }
        final String name1 = var1.getName();
        final String name2 = var2.getName();
        if(name1 == null){
            if (name2 != null) {
                return false;
            }
        } else if (name2 == null) {
            return false;
        } else if (!name1.equals(name2)) {
            return false;
        }
        final PsiExpression initializer1 = var1.getInitializer();
        final PsiExpression initializer2 = var2.getInitializer();
        return expressionsAreEquivalent(initializer1, initializer2);
    }

    private static boolean tryStatementsAreEquivalent(
            @NotNull PsiTryStatement statement1,
            @NotNull PsiTryStatement statement2) {
        final PsiCodeBlock tryBlock1 = statement1.getTryBlock();
        final PsiCodeBlock tryBlock2 = statement2.getTryBlock();
        if(!codeBlocksAreEquivalent(tryBlock1, tryBlock2)){
            return false;
        }
        final PsiCodeBlock finallyBlock1 = statement1.getFinallyBlock();
        final PsiCodeBlock finallyBlock2 = statement2.getFinallyBlock();
        if(!codeBlocksAreEquivalent(finallyBlock1, finallyBlock2)){
            return false;
        }
        final PsiCodeBlock[] catchBlocks1 = statement1.getCatchBlocks();
        final PsiCodeBlock[] catchBlocks2 = statement2.getCatchBlocks();
        if(catchBlocks1.length != catchBlocks2.length){
            return false;
        }
        for(int i = 0; i < catchBlocks2.length; i++){
            if(!codeBlocksAreEquivalent(catchBlocks1[i], catchBlocks2[i])){
                return false;
            }
        }
        final PsiParameter[] catchParameters1 =
                statement1.getCatchBlockParameters();
        final PsiParameter[] catchParameters2 =
                statement2.getCatchBlockParameters();
        if(catchParameters1.length != catchParameters2.length){
            return false;
        }
        for(int i = 0; i < catchParameters2.length; i++){
            if(!parametersAreEquivalent(catchParameters2[i],
                    catchParameters1[i])){
                return false;
            }
        }
        return true;
    }

    private static boolean parametersAreEquivalent(
            @NotNull PsiParameter parameter1,
            @NotNull PsiParameter parameter2) {
        final PsiType type1 = parameter1.getType();
        final PsiType type2 = parameter2.getType();
        if(!typesAreEquivalent(type1, type2)){
            return false;
        }
        final String name1 = parameter1.getName();
        final String name2 = parameter2.getName();
        if(name1 == null){
            return name2 == null;
        }
        return name1.equals(name2);
    }

    public static boolean typesAreEquivalent(
            @Nullable PsiType type1, @Nullable PsiType type2){
        if(type1 == null){
            return type2 == null;
        }
        if(type2 == null){
            return false;
        }
        final String type1Text = type1.getCanonicalText();
        final String type2Text = type2.getCanonicalText();
        return type1Text.equals(type2Text);
    }

    private static boolean whileStatementsAreEquivalent(
            @NotNull PsiWhileStatement statement1,
            @NotNull PsiWhileStatement statement2){
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(body1, body2);
    }

    private static boolean forStatementsAreEquivalent(
            @NotNull PsiForStatement statement1,
            @NotNull PsiForStatement statement2) {
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        if(!expressionsAreEquivalent(condition1, condition2)){
            return false;
        }
        final PsiStatement initialization1 = statement1.getInitialization();
        final PsiStatement initialization2 = statement2.getInitialization();
        if(!statementsAreEquivalent(initialization1, initialization2)){
            return false;
        }
        final PsiStatement update1 = statement1.getUpdate();
        final PsiStatement update2 = statement2.getUpdate();
        if(!statementsAreEquivalent(update1, update2)){
            return false;
        }
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return statementsAreEquivalent(body1, body2);
    }

    private static boolean forEachStatementsAreEquivalent(
            @NotNull PsiForeachStatement statement1,
            @NotNull PsiForeachStatement statement2) {
        final PsiExpression value1 = statement1.getIteratedValue();
        final PsiExpression value2 = statement2.getIteratedValue();
        if(!expressionsAreEquivalent(value1, value2)){
            return false;
        }
        final PsiParameter parameter1 = statement1.getIterationParameter();
        final PsiParameter parameter2 = statement1.getIterationParameter();
        final String name1 = parameter1.getName();
        final String name2 = parameter2.getName();
        if(name1 == null) {
            if(name2 != null){
                return false;
            }
        } else if (name2 == null) {
            return false;
        } else if (!name1.equals(name2)){
            return false;
        }
        final PsiType type1 = parameter1.getType();
        if(!type1.equals(parameter2.getType())){
            return false;
        }
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return statementsAreEquivalent(body1, body2);
    }

    private static boolean switchStatementsAreEquivalent(
            @NotNull PsiSwitchStatement statement1,
            @NotNull PsiSwitchStatement statement2) {
        final PsiExpression switchExpression1 = statement1.getExpression();
        final PsiExpression swithcExpression2 = statement2.getExpression();
        final PsiCodeBlock body1 = statement1.getBody();
        final PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(switchExpression1, swithcExpression2) &&
                codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean doWhileStatementsAreEquivalent(
            @NotNull PsiDoWhileStatement statement1,
            @NotNull PsiDoWhileStatement statement2) {
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(body1, body2);
    }

    private static boolean assertStatementsAreEquivalent(
            @NotNull PsiAssertStatement statement1,
            @NotNull PsiAssertStatement statement2) {
        final PsiExpression condition1 = statement1.getAssertCondition();
        final PsiExpression condition2 = statement2.getAssertCondition();
        final PsiExpression description1 = statement1.getAssertDescription();
        final PsiExpression description2 = statement2.getAssertDescription();
        return expressionsAreEquivalent(condition1, condition2) &&
                expressionsAreEquivalent(description1, description2);
    }

    private static boolean synchronizedStatementsAreEquivalent(
            @NotNull PsiSynchronizedStatement statement1,
            @NotNull PsiSynchronizedStatement statement2) {
        final PsiExpression lock1 = statement1.getLockExpression();
        final PsiExpression lock2 = statement2.getLockExpression();
        final PsiCodeBlock body1 = statement1.getBody();
        final PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(lock1, lock2) &&
                codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean blockStatementsAreEquivalent(
            @NotNull PsiBlockStatement statement1,
            @NotNull PsiBlockStatement statement2){
        final PsiCodeBlock block1 = statement1.getCodeBlock();
        final PsiCodeBlock block2 = statement2.getCodeBlock();
        return codeBlocksAreEquivalent(block1, block2);
    }

    private static boolean breakStatementsAreEquivalent(
            @NotNull PsiBreakStatement statement1,
            @NotNull PsiBreakStatement statement2) {
        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if(identifier1 == null){
            return identifier2 == null;
        }
        if(identifier2 == null){
            return false;
        }
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean continueStatementsAreEquivalent(
            @NotNull PsiContinueStatement statement1,
            @NotNull PsiContinueStatement statement2) {
        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if(identifier1 == null){
            return identifier2 == null;
        }
        if(identifier2 == null){
            return false;
        }
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean switchLabelStatementsAreEquivalent(
            @NotNull PsiSwitchLabelStatement statement1,
            @NotNull PsiSwitchLabelStatement statement2) {
        if (statement1.isDefaultCase()){
            return statement2.isDefaultCase();
        }
        if(statement2.isDefaultCase()){
            return false;
        }
        final PsiExpression caseExpression1 = statement1.getCaseValue();
        final PsiExpression caseExpression2 = statement2.getCaseValue();
        return expressionsAreEquivalent(caseExpression1, caseExpression2);
    }

    private static boolean labeledStatementsAreEquivalent(
            @NotNull PsiLabeledStatement statement1,
            @NotNull PsiLabeledStatement statement2) {
        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    public static boolean codeBlocksAreEquivalent(
            @Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
        if (block1 == null && block2 == null){
            return true;
        }
        if(block1 == null || block2 == null){
            return false;
        }
        final PsiStatement[] statements1 = block1.getStatements();
        final PsiStatement[] statements2 = block2.getStatements();
        if(statements2.length != statements1.length){
            return false;
        }
        for(int i = 0; i < statements2.length; i++){
            if(!statementsAreEquivalent(statements2[i], statements1[i])){
                return false;
            }
        }
        return true;
    }

    private static boolean ifStatementsAreEquivalent(
            @NotNull PsiIfStatement statement1,
            @NotNull PsiIfStatement statement2) {
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement thenBranch1 = statement1.getThenBranch();
        final PsiStatement thenBranch2 = statement2.getThenBranch();
        final PsiStatement elseBranch1 = statement1.getElseBranch();
        final PsiStatement elseBranch2 = statement2.getElseBranch();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(thenBranch1, thenBranch2) &&
                statementsAreEquivalent(elseBranch1, elseBranch2);
    }

    private static boolean expressionStatementsAreEquivalent(
            @NotNull PsiExpressionStatement statement1,
            @NotNull PsiExpressionStatement statement2) {
        final PsiExpression expression1 = statement1.getExpression();
        final PsiExpression expression2 = statement2.getExpression();
        return expressionsAreEquivalent(expression1, expression2);
    }

    private static boolean returnStatementsAreEquivalent(
            @NotNull PsiReturnStatement statement1,
            @NotNull PsiReturnStatement statement2) {
        final PsiExpression returnValue1 = statement1.getReturnValue();
        final PsiExpression returnValue2 = statement2.getReturnValue();
        return expressionsAreEquivalent(returnValue1, returnValue2);
    }

    private static boolean throwStatementsAreEquivalent(
            @NotNull PsiThrowStatement statement1,
            @NotNull PsiThrowStatement statement2) {
        final PsiExpression exception1 = statement1.getException();
        final PsiExpression exception2 = statement2.getException();
        return expressionsAreEquivalent(exception1, exception2);
    }

    private static boolean expressionListStatementsAreEquivalent(
            @NotNull PsiExpressionListStatement statement1,
            @NotNull PsiExpressionListStatement statement2) {
        final PsiExpressionList expressionList1 =
                statement1.getExpressionList();
        final PsiExpression[] expressions1 = expressionList1.getExpressions();
        final PsiExpressionList expressionList2 =
                statement2.getExpressionList();
        final PsiExpression[] expressions2 = expressionList2.getExpressions();
        return expressionListsAreEquivalent(expressions1, expressions2);
    }

    public static boolean expressionsAreEquivalent(
            @Nullable PsiExpression exp1, @Nullable PsiExpression exp2) {
        if (exp1 == null && exp2 == null){
            return true;
        }
        if(exp1 == null || exp2 == null){
            return false;
        }
        PsiExpression expToCompare1 = exp1;
        while(expToCompare1 instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expToCompare1;
            expToCompare1 = parenthesizedExpression.getExpression();
        }
        PsiExpression expToCompare2 = exp2;
        while(expToCompare2 instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expToCompare2;
            expToCompare2 = parenthesizedExpression.getExpression();
        }
        if (expToCompare1 == null && expToCompare2 == null){
            return true;
        }
        if(expToCompare1 == null || expToCompare2 == null){
            return false;
        }
        if (expToCompare1.getClass() != expToCompare2.getClass()) {
            return false;
        }
        if (expToCompare1 instanceof PsiThisExpression) {
            return true;
        } else if (expToCompare1 instanceof PsiSuperExpression) {
            return true;
        } else if (expToCompare1 instanceof PsiLiteralExpression) {
            final String text1 = expToCompare1.getText();
            final String text2 = expToCompare2.getText();
            return text1.equals(text2);
        } else if (expToCompare1 instanceof PsiClassObjectAccessExpression) {
            final String text1 = expToCompare1.getText();
            final String text2 = expToCompare2.getText();
            return text1.equals(text2);
        } else if (expToCompare1 instanceof PsiReferenceExpression) {
            return referenceExpressionsAreEquivalent(
                    (PsiReferenceExpression) expToCompare1,
                    (PsiReferenceExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiMethodCallExpression) {
            return methodCallExpressionsAreEquivalent(
                    (PsiMethodCallExpression) expToCompare1,
                    (PsiMethodCallExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiNewExpression) {
            return newExpressionsAreEquivalent(
                    (PsiNewExpression) expToCompare1,
                    (PsiNewExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiArrayInitializerExpression) {
            return arrayInitializerExpressionsAreEquivalent(
                    (PsiArrayInitializerExpression) expToCompare1,
                    (PsiArrayInitializerExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiTypeCastExpression) {
            return typecastExpressionsAreEquivalent(
                    (PsiTypeCastExpression) expToCompare1,
                    (PsiTypeCastExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiArrayAccessExpression) {
            return arrayAccessExpressionsAreEquivalent(
                    (PsiArrayAccessExpression) expToCompare2,
                    (PsiArrayAccessExpression) expToCompare1);
        } else if (expToCompare1 instanceof PsiPrefixExpression) {
            return prefixExpressionsAreEquivalent(
                    (PsiPrefixExpression) expToCompare1,
                    (PsiPrefixExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiPostfixExpression) {
            return postfixExpressionsAreEquivalent(
                    (PsiPostfixExpression) expToCompare1,
                    (PsiPostfixExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiBinaryExpression) {
            return binaryExpressionsAreEquivalent(
                    (PsiBinaryExpression) expToCompare1,
                    (PsiBinaryExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiAssignmentExpression) {
            return assignmentExpressionsAreEquivalent(
                    (PsiAssignmentExpression) expToCompare1,
                    (PsiAssignmentExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiConditionalExpression) {
            return conditionalExpressionsAreEquivalent(
                    (PsiConditionalExpression) expToCompare1,
                    (PsiConditionalExpression) expToCompare2);
        } else if (expToCompare1 instanceof PsiInstanceOfExpression) {
            return instanceofExpressionsAreEquivalent(
                    (PsiInstanceOfExpression) expToCompare1,
                    (PsiInstanceOfExpression) expToCompare2);
        }
        return false;
    }

    private static boolean referenceExpressionsAreEquivalent(
            PsiReferenceExpression referenceExpression1,
            PsiReferenceExpression referenceExpression2) {
        final PsiExpression qualifier1 =
                referenceExpression1.getQualifierExpression();
        final PsiExpression qualifier2 =
                referenceExpression2.getQualifierExpression();
        if (qualifier1 != null &&
                !(qualifier1 instanceof PsiThisExpression ||
                        qualifier1 instanceof PsiSuperExpression)) {
            if (qualifier2 == null) {
                return false;
            } else if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
                return false;
            }
        } else {
            if (qualifier2 != null &&
                    !(qualifier2 instanceof PsiThisExpression ||
                            qualifier2 instanceof PsiSuperExpression)) {
                return false;
            }
        }
        final PsiElement element1 = referenceExpression1.resolve();
        final PsiElement element2 = referenceExpression2.resolve();
        if (element1 instanceof PsiField) {
            if (!(element2 instanceof PsiField)) {
                return false;
            }
            final PsiField field1 = (PsiField)element1;
            final PsiField field2 = (PsiField)element2;
            final String name1 = field1.getName();
            final String name2 = field2.getName();
            if (name1 == null) {
                if (name2 != null) {
                    return false;
                }
            } else if (name2 == null) {
                return false;
            } else if (!name1.equals(name2)) {
                return false;
            }
            final PsiClass containingClass1 = field1.getContainingClass();
            final PsiClass containingClass2 = field2.getContainingClass();
            final String qualifiedName1 = containingClass1.getQualifiedName();
            final String qualifiedName2 = containingClass2.getQualifiedName();
            if (qualifiedName1 == null) {
                return qualifiedName2 == null;
            } else if (qualifiedName2 == null) {
                return false;
            }
            return qualifiedName1.equals(qualifiedName2);
        }
        final String text1 = referenceExpression1.getText();
        final String text2 = referenceExpression2.getText();
        return text1.equals(text2);
    }

    private static boolean instanceofExpressionsAreEquivalent(
            PsiInstanceOfExpression instanceOfExpression1,
            PsiInstanceOfExpression instanceOfExpression2) {
        final PsiExpression operand1 = instanceOfExpression1.getOperand();
        final PsiExpression operand2 = instanceOfExpression2.getOperand();
        if (!expressionsAreEquivalent(operand1, operand2)) {
            return false;
        }
        final PsiTypeElement typeElement1 =
                instanceOfExpression1.getCheckType();
        final PsiTypeElement typeElement2 =
                instanceOfExpression2.getCheckType();
        if (typeElement1 == null) {
            return  typeElement2 == null;
        } else if (typeElement2 == null) {
            return false;
        }
        final PsiType type1 = typeElement1.getType();
        final PsiType type2 = typeElement2.getType();
        return typesAreEquivalent(type1, type2);
    }

    private static boolean methodCallExpressionsAreEquivalent(
            @NotNull PsiMethodCallExpression methodExp1,
            @NotNull PsiMethodCallExpression methodExp2){
        final PsiReferenceExpression methodExpression1 =
                methodExp1.getMethodExpression();
        final PsiReferenceExpression methodExpression2 =
                methodExp2.getMethodExpression();
        if(!expressionsAreEquivalent(methodExpression1, methodExpression2)){
            return false;
        }
        final PsiExpressionList argumentList1 = methodExp1.getArgumentList();
        final PsiExpression[] args1 = argumentList1.getExpressions();
        final PsiExpressionList argumentList2 = methodExp2.getArgumentList();
        final PsiExpression[] args2 = argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean newExpressionsAreEquivalent(
            @NotNull PsiNewExpression newExp1,
            @NotNull PsiNewExpression newExp2) {
        final PsiJavaCodeReferenceElement classRef1 =
                newExp1.getClassReference();
        final PsiJavaCodeReferenceElement classRef2 =
                newExp2.getClassReference();
        if (classRef1 == null || classRef2 == null) {
            return false;
        }
        final String text = classRef1.getText();
        if (!text.equals(classRef2.getText())) {
            return false;
        }
        final PsiExpression[] arrayDimensions1 = newExp1.getArrayDimensions();
        final PsiExpression[] arrayDimensions2 = newExp2.getArrayDimensions();
        if (!expressionListsAreEquivalent(arrayDimensions1, arrayDimensions2)) {
            return false;
        }
        final PsiArrayInitializerExpression arrayInitializer1 =
                newExp1.getArrayInitializer();
        final PsiArrayInitializerExpression arrayInitializer2 =
                newExp2.getArrayInitializer();
        if (!expressionsAreEquivalent(arrayInitializer1, arrayInitializer2)) {
            return false;
        }
        final PsiExpression qualifier1 = newExp1.getQualifier();
        final PsiExpression qualifier2 = newExp2.getQualifier();
        if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
            return false;
        }
        final PsiExpressionList argumentList1 = newExp1.getArgumentList();
        final PsiExpression[] args1;
        if (argumentList1 == null) {
            args1 = null;
        } else {
            args1 = argumentList1.getExpressions();
        }
        final PsiExpressionList argumentList2 = newExp2.getArgumentList();
        final PsiExpression[] args2;
        if (argumentList2 == null) {
            args2 = null;
        } else {
            args2 = argumentList2.getExpressions();
        }
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean arrayInitializerExpressionsAreEquivalent(
            @NotNull PsiArrayInitializerExpression arrInitExp1,
            @NotNull PsiArrayInitializerExpression arrInitExp2){
        final PsiExpression[] initializers1 = arrInitExp1.getInitializers();
        final PsiExpression[] initializers2 = arrInitExp2.getInitializers();
        return expressionListsAreEquivalent(initializers1, initializers2);
    }

    private static boolean typecastExpressionsAreEquivalent(
            @NotNull PsiTypeCastExpression typecastExp1,
            @NotNull PsiTypeCastExpression typecastExp2) {
        final PsiTypeElement typeElement1 = typecastExp1.getCastType();
        final PsiTypeElement typeElement2 = typecastExp2.getCastType();
        if (typeElement1 == null && typeElement2 == null) {
            return true;
        }
        if (typeElement1 == null || typeElement2 == null) {
            return false;
        }
        final PsiType type1 = typeElement1.getType();
        final PsiType type2 = typeElement2.getType();
        if(!typesAreEquivalent(type1, type2)) {
            return false;
        }
        final PsiExpression operand1 = typecastExp1.getOperand();
        final PsiExpression operand2 = typecastExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean arrayAccessExpressionsAreEquivalent(
            @NotNull PsiArrayAccessExpression arrAccessExp2,
            @NotNull PsiArrayAccessExpression arrAccessExp1){
        final PsiExpression arrayExpression2 =
                arrAccessExp2.getArrayExpression();
        final PsiExpression arrayExpression1 =
                arrAccessExp1.getArrayExpression();
        final PsiExpression indexExpression2 =
                arrAccessExp2.getIndexExpression();
        final PsiExpression indexExpression1 =
                arrAccessExp1.getIndexExpression();
        return expressionsAreEquivalent(arrayExpression2, arrayExpression1)
                && expressionsAreEquivalent(indexExpression2, indexExpression1);
    }

    private static boolean prefixExpressionsAreEquivalent(
            @NotNull PsiPrefixExpression prefixExp1,
            @NotNull PsiPrefixExpression prefixExp2){
        final PsiJavaToken sign1 = prefixExp1.getOperationSign();
        final PsiJavaToken sign2 = prefixExp2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression operand1 = prefixExp1.getOperand();
        final PsiExpression operand2 = prefixExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean postfixExpressionsAreEquivalent(
            @NotNull PsiPostfixExpression postfixExp1,
            @NotNull PsiPostfixExpression postfixExp2){
        final PsiJavaToken sign1 = postfixExp1.getOperationSign();
        final PsiJavaToken sign2 = postfixExp2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression operand1 = postfixExp1.getOperand();
        final PsiExpression operand2 = postfixExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean binaryExpressionsAreEquivalent(
            @NotNull PsiBinaryExpression binaryExp1,
            @NotNull PsiBinaryExpression binaryExp2){
        final PsiJavaToken sign1 = binaryExp1.getOperationSign();
        final PsiJavaToken sign2 = binaryExp2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression lhs1 = binaryExp1.getLOperand();
        final PsiExpression lhs2 = binaryExp2.getLOperand();
        final PsiExpression rhs1 = binaryExp1.getROperand();
        final PsiExpression rhs2 = binaryExp2.getROperand();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean assignmentExpressionsAreEquivalent(
            @NotNull PsiAssignmentExpression assignExp1,
            @NotNull PsiAssignmentExpression assignExp2){
        final PsiJavaToken sign1 = assignExp1.getOperationSign();
        final PsiJavaToken sign2 = assignExp2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression lhs1 = assignExp1.getLExpression();
        final PsiExpression lhs2 = assignExp2.getLExpression();
        final PsiExpression rhs1 = assignExp1.getRExpression();
        final PsiExpression rhs2 = assignExp2.getRExpression();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean conditionalExpressionsAreEquivalent(
            @NotNull PsiConditionalExpression condExp1,
            @NotNull PsiConditionalExpression condExp2){
        final PsiExpression condition1 = condExp1.getCondition();
        final PsiExpression condition2 = condExp2.getCondition();
        final PsiExpression thenExpression1 = condExp1.getThenExpression();
        final PsiExpression thenExpression2 = condExp2.getThenExpression();
        final PsiExpression elseExpression1 = condExp1.getElseExpression();
        final PsiExpression elseExpression2 = condExp2.getElseExpression();
        return expressionsAreEquivalent(condition1, condition2)
                && expressionsAreEquivalent(thenExpression1, thenExpression2)
                && expressionsAreEquivalent(elseExpression1, elseExpression2);
    }

    private static boolean expressionListsAreEquivalent(
            @Nullable PsiExpression[] expressions1,
            @Nullable PsiExpression[] expressions2){
        if(expressions1 == null && expressions2 == null){
            return true;
        }
        if(expressions1 == null || expressions2 == null){
            return false;
        }
        if(expressions1.length != expressions2.length){
            return false;
        }
        for(int i = 0; i < expressions1.length; i++){
            if(!expressionsAreEquivalent(expressions1[i], expressions2[i])){
                return false;
            }
        }
        return true;
    }
}