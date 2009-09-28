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
package com.siyeh.ipp.psiutils;

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

    private static final int THIS_EXPRESSION = 0;
    private static final int LITERAL_EXPRESSION = 1;
    private static final int CLASS_OBJECT_EXPRESSION = 2;
    private static final int REFERENCE_EXPRESSION = 3;
    private static final int SUPER_EXPRESSION = 4;
    private static final int METHOD_CALL_EXPRESSION = 5;
    private static final int NEW_EXPRESSION = 6;
    private static final int ARRAY_INITIALIZER_EXPRESSION = 7;
    private static final int TYPECAST_EXPRESSION = 8;
    private static final int ARRAY_ACCESS_EXPRESSION = 9;
    private static final int PREFIX_EXPRESSION = 10;
    private static final int POSTFIX_EXPRESSION = 11;
    private static final int BINARY_EXPRESSION = 12;
    private static final int CONDITIONAL_EXPRESSION = 13;
    private static final int ASSIGNMENT_EXPRESSION = 14;
    private static final int ASSERT_STATEMENT = 0;
    private static final int BLOCK_STATEMENT = 1;
    private static final int BREAK_STATEMENT = 2;
    private static final int CONTINUE_STATEMENT = 3;
    private static final int DECLARATION_STATEMENT = 4;
    private static final int DO_WHILE_STATEMENT = 5;
    private static final int EMPTY_STATEMENT = 6;
    private static final int EXPRESSION_LIST_STATEMENT = 7;
    private static final int EXPRESSION_STATEMENT = 8;
    private static final int FOR_STATEMENT = 9;
    private static final int IF_STATEMENT = 10;
    private static final int LABELED_STATEMENT = 11;
    private static final int RETURN_STATEMENT = 12;
    private static final int SWITCH_LABEL_STATEMENT = 13;
    private static final int SWITCH_STATEMENT = 14;
    private static final int SYNCHRONIZED_STATEMENT = 15;
    private static final int THROW_STATEMENT = 16;
    private static final int TRY_STATEMENT = 17;
    private static final int WHILE_STATEMENT = 18;
    private static final int FOR_EACH_STATEMENT = 19;

    public static boolean modifierListsAreEquivalent(
            @Nullable PsiModifierList list1, @Nullable PsiModifierList list2){
        if(list1 == null){
            return list2 == null;
        } else if(list2 == null){
            return false;
        }
        final PsiAnnotation[] annotations = list1.getAnnotations();
        for (PsiAnnotation annotation : annotations){
            final String qualifiedName = annotation.getQualifiedName();
            if(qualifiedName == null){
                return false;
            }
            if(list2.findAnnotation(qualifiedName) == null){
                return false;
            }
        }
        if(list1.hasModifierProperty(PsiModifier.ABSTRACT) &&
                !list2.hasModifierProperty(PsiModifier.ABSTRACT)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.FINAL) &&
                !list2.hasModifierProperty(PsiModifier.FINAL)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.NATIVE) &&
                !list2.hasModifierProperty(PsiModifier.NATIVE)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                !list2.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.PRIVATE) &&
                !list2.hasModifierProperty(PsiModifier.PRIVATE)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.PROTECTED) &&
                !list2.hasModifierProperty(PsiModifier.PROTECTED)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.PUBLIC) &&
                !list2.hasModifierProperty(PsiModifier.PUBLIC)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.STATIC) &&
                !list2.hasModifierProperty(PsiModifier.STATIC)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.STRICTFP) &&
                !list2.hasModifierProperty(PsiModifier.STRICTFP)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.SYNCHRONIZED) &&
                !list2.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
            return false;
        }
        if(list1.hasModifierProperty(PsiModifier.TRANSIENT) &&
                !list2.hasModifierProperty(PsiModifier.TRANSIENT)){
            return false;
        }
        return !(list1.hasModifierProperty(PsiModifier.VOLATILE) &&
                !list2.hasModifierProperty(PsiModifier.VOLATILE));
    }

    public static boolean statementsAreEquivalent(
            @Nullable PsiStatement statement1,
            @Nullable PsiStatement statement2){
        if(statement1 == null && statement2 == null){
            return true;
        }
        if(statement1 == null || statement2 == null){
            return false;
        }
        final int type1 = getStatementType(statement1);
        final int type2 = getStatementType(statement2);
        if(type1 != type2){
            return false;
        }
        switch(type1){
            case ASSERT_STATEMENT:
                return assertStatementsAreEquivalent(
                        (PsiAssertStatement)statement1,
                        (PsiAssertStatement)statement2);
            case BLOCK_STATEMENT:
                return blockStatementsAreEquivalent(
                        (PsiBlockStatement)statement1,
                        (PsiBlockStatement)statement2);
            case BREAK_STATEMENT:
                return breakStatementsAreEquivalent(
                        (PsiBreakStatement)statement1,
                        (PsiBreakStatement)statement2);
            case CONTINUE_STATEMENT:
                return continueStatementsAreEquivalent(
                        (PsiContinueStatement)statement1,
                        (PsiContinueStatement)statement2);
            case DECLARATION_STATEMENT:
                return declarationStatementsAreEquivalent(
                        (PsiDeclarationStatement)statement1,
                        (PsiDeclarationStatement)statement2);
            case DO_WHILE_STATEMENT:
                return doWhileStatementsAreEquivalent(
                        (PsiDoWhileStatement)statement1,
                        (PsiDoWhileStatement)statement2);
            case EMPTY_STATEMENT:
                return true;
            case EXPRESSION_LIST_STATEMENT:
                return expressionListStatementsAreEquivalent(
                        (PsiExpressionListStatement)statement1,
                        (PsiExpressionListStatement)statement2);
            case EXPRESSION_STATEMENT:
                return expressionStatementsAreEquivalent(
                        (PsiExpressionStatement)statement1,
                        (PsiExpressionStatement)statement2);
            case FOR_STATEMENT:
                return forStatementsAreEquivalent(
                        (PsiForStatement)statement1,
                        (PsiForStatement)statement2);
            case FOR_EACH_STATEMENT:
                return forEachStatementsAreEquivalent(
                        (PsiForeachStatement)statement1,
                        (PsiForeachStatement)statement2);
            case IF_STATEMENT:
                return ifStatementsAreEquivalent(
                        (PsiIfStatement) statement1,
                        (PsiIfStatement) statement2);
            case LABELED_STATEMENT:
                return labeledStatementsAreEquivalent(
                        (PsiLabeledStatement)statement1,
                        (PsiLabeledStatement)statement2);
            case RETURN_STATEMENT:
                return returnStatementsAreEquivalent(
                        (PsiReturnStatement)statement1,
                        (PsiReturnStatement)statement2);
            case SWITCH_LABEL_STATEMENT:
                return switchLabelStatementsAreEquivalent(
                        (PsiSwitchLabelStatement)statement1,
                        (PsiSwitchLabelStatement)statement2);
            case SWITCH_STATEMENT:
                return switchStatementsAreEquivalent(
                        (PsiSwitchStatement)statement1,
                        (PsiSwitchStatement)statement2);
            case SYNCHRONIZED_STATEMENT:
                return synchronizedStatementsAreEquivalent(
                        (PsiSynchronizedStatement)statement1,
                        (PsiSynchronizedStatement)statement2);
            case THROW_STATEMENT:
                return throwStatementsAreEquivalent(
                        (PsiThrowStatement)statement1,
                        (PsiThrowStatement)statement2);
            case TRY_STATEMENT:
                return tryStatementsAreEquivalent(
                        (PsiTryStatement)statement1,
                        (PsiTryStatement)statement2);
            case WHILE_STATEMENT:
                return whileStatementsAreEquivalent(
                        (PsiWhileStatement)statement1,
                        (PsiWhileStatement)statement2);
            default:
                final String text1 = statement1.getText();
                final String text2 = statement2.getText();
                return text1.equals(text2);
        }
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
            @NotNull  PsiLocalVariable localVariable1,
            @NotNull PsiLocalVariable localVariable2){
        final PsiType type1 = localVariable1.getType();
        final PsiType type2 = localVariable2.getType();
        if(!typesAreEquivalent(type1, type2)){
            return false;
        }
        final String name1 = localVariable1.getName();
        final String name2 = localVariable2.getName();
        if(name1 == null){
            return name2 == null;
        }
        if(!name1.equals(name2)){
            return false;
        }
        final PsiExpression initializer1 = localVariable1.getInitializer();
        final PsiExpression initializer2 = localVariable2.getInitializer();
        return expressionsAreEquivalent(initializer1, initializer2);
    }

    private static boolean tryStatementsAreEquivalent(
            @NotNull PsiTryStatement statement1,
            @NotNull PsiTryStatement statement2){
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
            @NotNull PsiParameter parameter2){
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

    private static boolean typesAreEquivalent(
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
            @NotNull PsiForStatement statement2){
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
            @NotNull PsiForeachStatement statement2){
        final PsiExpression value1 = statement1.getIteratedValue();
        final PsiExpression value2 = statement2.getIteratedValue();
        if(!expressionsAreEquivalent(value1, value2)){
            return false;
        }
        final PsiParameter parameter1 = statement1.getIterationParameter();
        final PsiParameter parameter2 = statement1.getIterationParameter();
        final String name1 = parameter1.getName();
        if(name1 == null){
            return parameter2.getName() == null;
        }
        if(!name1.equals(parameter2.getName())){
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
            @NotNull PsiSwitchStatement statement2){
        final PsiExpression switchExpression1 = statement1.getExpression();
        final PsiExpression swithcExpression2 = statement2.getExpression();
        final PsiCodeBlock body1 = statement1.getBody();
        final PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(switchExpression1, swithcExpression2) &&
                codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean doWhileStatementsAreEquivalent(
            @NotNull PsiDoWhileStatement statement1,
            @NotNull PsiDoWhileStatement statement2){
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(body1, body2);
    }

    private static boolean assertStatementsAreEquivalent(
            @NotNull PsiAssertStatement statement1,
            @NotNull PsiAssertStatement statement2){
        final PsiExpression condition1 = statement1.getAssertCondition();
        final PsiExpression condition2 = statement2.getAssertCondition();
        final PsiExpression description1 = statement1.getAssertDescription();
        final PsiExpression description2 = statement2.getAssertDescription();
        return expressionsAreEquivalent(condition1, condition2) &&
                expressionsAreEquivalent(description1, description2);
    }

    private static boolean synchronizedStatementsAreEquivalent(
            @NotNull PsiSynchronizedStatement statement1,
            @NotNull PsiSynchronizedStatement statement2){
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
            @NotNull PsiBreakStatement statement2){
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
            @NotNull PsiContinueStatement statement2){
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
            @NotNull PsiSwitchLabelStatement statement2){
        if(statement1.isDefaultCase()){
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
            @NotNull PsiLabeledStatement statement2){
        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    public static boolean codeBlocksAreEquivalent(
            @Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2){
        if(block1 == null && block2 == null){
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
            @NotNull PsiIfStatement statement2){
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
            @NotNull PsiExpressionStatement statement2){
        final PsiExpression expression1 = statement1.getExpression();
        final PsiExpression expression2 = statement2.getExpression();
        return expressionsAreEquivalent(expression1, expression2);
    }

    private static boolean returnStatementsAreEquivalent(
            @NotNull PsiReturnStatement statement1,
            @NotNull PsiReturnStatement statement2){
        final PsiExpression returnValue1 = statement1.getReturnValue();
        final PsiExpression returnValue2 = statement2.getReturnValue();
        return expressionsAreEquivalent(returnValue1, returnValue2);
    }

    private static boolean throwStatementsAreEquivalent(
            @NotNull PsiThrowStatement statement1,
            @NotNull PsiThrowStatement statement2){
        final PsiExpression exception1 = statement1.getException();
        final PsiExpression exception2 = statement2.getException();
        return expressionsAreEquivalent(exception1, exception2);
    }

    private static boolean expressionListStatementsAreEquivalent(
            @NotNull PsiExpressionListStatement statement1,
            @NotNull PsiExpressionListStatement statement2){
        final PsiExpressionList expressionList1 =
                statement1.getExpressionList();
        final PsiExpression[] expressions1 = expressionList1.getExpressions();
        final PsiExpressionList expressionList2 =
                statement2.getExpressionList();
        final PsiExpression[] expressions2 = expressionList2.getExpressions();
        return expressionListsAreEquivalent(expressions1, expressions2);
    }

    public static boolean expressionsAreEquivalent(
            @Nullable PsiExpression expression1,
            @Nullable PsiExpression expression2){
        if(expression1 == null && expression2 == null){
            return true;
        }
        if(expression1 == null || expression2 == null){
            return false;
        }
        PsiExpression expToCompare1 = expression1;
        while(expToCompare1 instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expToCompare1;
            expToCompare1 = parenthesizedExpression.getExpression();
        }
        PsiExpression expToCompare2 = expression2;
        while(expToCompare2 instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expToCompare2;
            expToCompare2 = parenthesizedExpression.getExpression();
        }
        if(expToCompare1 == null && expToCompare2 == null){
            return true;
        }
        if(expToCompare1 == null || expToCompare2 == null){
            return false;
        }
        final int type1 = getExpressionType(expToCompare1);
        final int type2 = getExpressionType(expToCompare2);
        if(type1 != type2){
            return false;
        }
        switch(type1){
            case THIS_EXPRESSION:
            case SUPER_EXPRESSION:
                return true;
            case LITERAL_EXPRESSION:
            case CLASS_OBJECT_EXPRESSION:
            case REFERENCE_EXPRESSION:
                final String text1 = expToCompare1.getText();
                final String text2 = expToCompare2.getText();
                return text1.equals(text2);
            case METHOD_CALL_EXPRESSION:
                return methodCallExpressionsAreEquivalent(
                        (PsiMethodCallExpression) expToCompare1,
                        (PsiMethodCallExpression) expToCompare2);
            case NEW_EXPRESSION:
                return newExpressionsAreEquivalent(
                        (PsiNewExpression) expToCompare1,
                        (PsiNewExpression) expToCompare2);
            case ARRAY_INITIALIZER_EXPRESSION:
                return arrayInitializerExpressionsAreEquivalent(
                        (PsiArrayInitializerExpression) expToCompare1,
                        (PsiArrayInitializerExpression) expToCompare2);
            case TYPECAST_EXPRESSION:
                return typecastExpressionsAreEquivalent(
                        (PsiTypeCastExpression) expToCompare2,
                        (PsiTypeCastExpression) expToCompare1);
            case ARRAY_ACCESS_EXPRESSION:
                return arrayAccessExpressionsAreEquivalent(
                        (PsiArrayAccessExpression) expToCompare2,
                        (PsiArrayAccessExpression) expToCompare1);
            case PREFIX_EXPRESSION:
                return prefixExpressionsAreEquivalent(
                        (PsiPrefixExpression) expToCompare1,
                        (PsiPrefixExpression) expToCompare2);
            case POSTFIX_EXPRESSION:
                return postfixExpressionsAreEquivalent(
                        (PsiPostfixExpression) expToCompare1,
                        (PsiPostfixExpression) expToCompare2);
            case BINARY_EXPRESSION:
                return binaryExpressionsAreEquivalent(
                        (PsiBinaryExpression) expToCompare1,
                        (PsiBinaryExpression) expToCompare2);
            case ASSIGNMENT_EXPRESSION:
                return assignmentExpressionsAreEquivalent(
                        (PsiAssignmentExpression) expToCompare1,
                        (PsiAssignmentExpression) expToCompare2);
            case CONDITIONAL_EXPRESSION:
                return conditionalExpressionsAreEquivalent(
                        (PsiConditionalExpression) expToCompare1,
                        (PsiConditionalExpression) expToCompare2);
            default:
                return false;
        }
    }

    private static boolean methodCallExpressionsAreEquivalent(
            @NotNull PsiMethodCallExpression methodCallExpression1,
            @NotNull PsiMethodCallExpression methodCallExpression2){
        final PsiReferenceExpression methodExpression1 =
                methodCallExpression1.getMethodExpression();
        final PsiReferenceExpression methodExpression2 =
                methodCallExpression2.getMethodExpression();
        if(!expressionsAreEquivalent(methodExpression1, methodExpression2)){
            return false;
        }
        final PsiExpressionList argumentList1 =
                methodCallExpression1.getArgumentList();
        final PsiExpression[] args1 = argumentList1.getExpressions();
        final PsiExpressionList argumentList2 =
                methodCallExpression2.getArgumentList();
        final PsiExpression[] args2 = argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean newExpressionsAreEquivalent(
            @NotNull PsiNewExpression newExpression1,
            @NotNull PsiNewExpression newExpression2){
        final PsiJavaCodeReferenceElement classReference1 =
                newExpression1.getClassReference();
        final PsiJavaCodeReferenceElement classReference2 =
                newExpression2.getClassReference();
        if(classReference1 == null || classReference2 == null){
            return false;
        }
        final String text = classReference1.getText();
        if(!text.equals(classReference2.getText())){
            return false;
        }
        final PsiExpression[] arrayDimensions1 =
                newExpression1.getArrayDimensions();
        final PsiExpression[] arrayDimensions2 =
                newExpression2.getArrayDimensions();
        if(!expressionListsAreEquivalent(arrayDimensions1, arrayDimensions2)){
            return false;
        }
        final PsiArrayInitializerExpression arrayInitializer1 =
                newExpression1.getArrayInitializer();
        final PsiArrayInitializerExpression arrayInitializer2 =
                newExpression2.getArrayInitializer();
        if(!expressionsAreEquivalent(arrayInitializer1, arrayInitializer2)){
            return false;
        }
        final PsiExpression qualifier1 = newExpression1.getQualifier();
        final PsiExpression qualifier2 = newExpression2.getQualifier();
        if(!expressionsAreEquivalent(qualifier1, qualifier2)){
            return false;
        }
        final PsiExpressionList argumentList1 = newExpression1.getArgumentList();
        final PsiExpression[] args1;
        if(argumentList1 == null){
            args1 = null;
        } else{
            args1 = argumentList1.getExpressions();
        }
        final PsiExpressionList argumentList2 = newExpression2.getArgumentList();
        final PsiExpression[] args2;
        if(argumentList2 == null){
            args2 = null;
        } else{
            args2 = argumentList2.getExpressions();
        }
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean arrayInitializerExpressionsAreEquivalent(
            @NotNull PsiArrayInitializerExpression arrayInitializerExpression1,
            @NotNull PsiArrayInitializerExpression arrayInitializerExpression2){
        final PsiExpression[] initializers1 =
                arrayInitializerExpression1.getInitializers();
        final PsiExpression[] initializers2 =
                arrayInitializerExpression2.getInitializers();
        return expressionListsAreEquivalent(initializers1, initializers2);
    }

    private static boolean typecastExpressionsAreEquivalent(
            @NotNull PsiTypeCastExpression typeCastExpression1,
            @NotNull PsiTypeCastExpression typeCastExpression2){
        final PsiTypeElement castType2 = typeCastExpression1.getCastType();
        final PsiTypeElement castType1 = typeCastExpression2.getCastType();
        if(castType1 == null && castType2 == null){
            return true;
        }
        if(castType1 == null || castType2 == null){
            return false;
        }
        if(!castType2.equals(castType1)){
            return false;
        }
        final PsiExpression operand1 = typeCastExpression2.getOperand();
        final PsiExpression operand2 = typeCastExpression1.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean arrayAccessExpressionsAreEquivalent(
            @NotNull PsiArrayAccessExpression arrayAccessExpression1,
            @NotNull PsiArrayAccessExpression arrayAccessExpression2){
        final PsiExpression arrayExpression2 =
                arrayAccessExpression1.getArrayExpression();
        final PsiExpression arrayExpression1 =
                arrayAccessExpression2.getArrayExpression();
        final PsiExpression indexExpression2 =
                arrayAccessExpression1.getIndexExpression();
        final PsiExpression indexExpression1 =
                arrayAccessExpression2.getIndexExpression();
        return expressionsAreEquivalent(arrayExpression2, arrayExpression1)
                && expressionsAreEquivalent(indexExpression2, indexExpression1);
    }

    private static boolean prefixExpressionsAreEquivalent(
            @NotNull PsiPrefixExpression prefixExpression1,
            @NotNull PsiPrefixExpression prefixExpression2){
        final PsiJavaToken sign1 = prefixExpression1.getOperationSign();
        final PsiJavaToken sign2 = prefixExpression2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression operand1 = prefixExpression1.getOperand();
        final PsiExpression operand2 = prefixExpression2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean postfixExpressionsAreEquivalent(
            @NotNull PsiPostfixExpression postfixExpression1,
            @NotNull PsiPostfixExpression postfixExpression2){
        final PsiJavaToken sign1 = postfixExpression1.getOperationSign();
        final PsiJavaToken sign2 = postfixExpression2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression operand1 = postfixExpression1.getOperand();
        final PsiExpression operand2 = postfixExpression2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean binaryExpressionsAreEquivalent(
            @NotNull PsiBinaryExpression binaryExpression1,
            @NotNull PsiBinaryExpression binaryExpression2){
        final PsiJavaToken sign1 = binaryExpression1.getOperationSign();
        final PsiJavaToken sign2 = binaryExpression2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression lhs1 = binaryExpression1.getLOperand();
        final PsiExpression lhs2 = binaryExpression2.getLOperand();
        final PsiExpression rhs1 = binaryExpression1.getROperand();
        final PsiExpression rhs2 = binaryExpression2.getROperand();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean assignmentExpressionsAreEquivalent(
            @NotNull PsiAssignmentExpression assignmentExpression1,
            @NotNull PsiAssignmentExpression assignmentExpression2){
        final PsiJavaToken sign1 = assignmentExpression1.getOperationSign();
        final PsiJavaToken sign2 = assignmentExpression2.getOperationSign();
        final IElementType tokenType1 = sign1.getTokenType();
        if(!tokenType1.equals(sign2.getTokenType())){
            return false;
        }
        final PsiExpression lhs1 = assignmentExpression1.getLExpression();
        final PsiExpression lhs2 = assignmentExpression2.getLExpression();
        final PsiExpression rhs1 = assignmentExpression1.getRExpression();
        final PsiExpression rhs2 = assignmentExpression2.getRExpression();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean conditionalExpressionsAreEquivalent(
            @NotNull PsiConditionalExpression conditionalExpression1,
            @NotNull PsiConditionalExpression conditionalExpression2){
        final PsiExpression condition1 = conditionalExpression1.getCondition();
        final PsiExpression condition2 = conditionalExpression2.getCondition();
        final PsiExpression thenExpression1 =
                conditionalExpression1.getThenExpression();
        final PsiExpression thenExpression2 =
                conditionalExpression2.getThenExpression();
        final PsiExpression elseExpression1 =
                conditionalExpression1.getElseExpression();
        final PsiExpression elseExpression2 =
                conditionalExpression2.getElseExpression();
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

    private static int getExpressionType(@Nullable PsiExpression expression){
        if(expression instanceof PsiThisExpression){
            return THIS_EXPRESSION;
        }
        if(expression instanceof PsiLiteralExpression){
            return LITERAL_EXPRESSION;
        }
        if(expression instanceof PsiClassObjectAccessExpression){
            return CLASS_OBJECT_EXPRESSION;
        }
        if(expression instanceof PsiReferenceExpression){
            return REFERENCE_EXPRESSION;
        }
        if(expression instanceof PsiSuperExpression){
            return SUPER_EXPRESSION;
        }
        if(expression instanceof PsiMethodCallExpression){
            return METHOD_CALL_EXPRESSION;
        }
        if(expression instanceof PsiNewExpression){
            return NEW_EXPRESSION;
        }
        if(expression instanceof PsiArrayInitializerExpression){
            return ARRAY_INITIALIZER_EXPRESSION;
        }
        if(expression instanceof PsiTypeCastExpression){
            return TYPECAST_EXPRESSION;
        }
        if(expression instanceof PsiArrayAccessExpression){
            return ARRAY_ACCESS_EXPRESSION;
        }
        if(expression instanceof PsiPrefixExpression){
            return PREFIX_EXPRESSION;
        }
        if(expression instanceof PsiPostfixExpression){
            return POSTFIX_EXPRESSION;
        }
        if(expression instanceof PsiBinaryExpression){
            return BINARY_EXPRESSION;
        }
        if(expression instanceof PsiConditionalExpression){
            return CONDITIONAL_EXPRESSION;
        }
        if(expression instanceof PsiAssignmentExpression){
            return ASSIGNMENT_EXPRESSION;
        }
        return -1;
    }

    private static int getStatementType(@Nullable PsiStatement statement){
        if(statement instanceof PsiAssertStatement){
            return ASSERT_STATEMENT;
        }
        if(statement instanceof PsiBlockStatement){
            return BLOCK_STATEMENT;
        }
        if(statement instanceof PsiBreakStatement){
            return BREAK_STATEMENT;
        }
        if(statement instanceof PsiContinueStatement){
            return CONTINUE_STATEMENT;
        }
        if(statement instanceof PsiDeclarationStatement){
            return DECLARATION_STATEMENT;
        }
        if(statement instanceof PsiDoWhileStatement){
            return DO_WHILE_STATEMENT;
        }
        if(statement instanceof PsiEmptyStatement){
            return EMPTY_STATEMENT;
        }
        if(statement instanceof PsiExpressionListStatement){
            return EXPRESSION_LIST_STATEMENT;
        }
        if(statement instanceof PsiExpressionStatement){
            return EXPRESSION_STATEMENT;
        }
        if(statement instanceof PsiForStatement){
            return FOR_STATEMENT;
        }
        if(statement instanceof PsiForeachStatement){
            return FOR_EACH_STATEMENT;
        }
        if(statement instanceof PsiIfStatement){
            return IF_STATEMENT;
        }
        if(statement instanceof PsiLabeledStatement){
            return LABELED_STATEMENT;
        }
        if(statement instanceof PsiReturnStatement){
            return RETURN_STATEMENT;
        }
        if(statement instanceof PsiSwitchLabelStatement){
            return SWITCH_LABEL_STATEMENT;
        }
        if(statement instanceof PsiSwitchStatement){
            return SWITCH_STATEMENT;
        }
        if(statement instanceof PsiSynchronizedStatement){
            return SYNCHRONIZED_STATEMENT;
        }
        if(statement instanceof PsiThrowStatement){
            return THROW_STATEMENT;
        }
        if(statement instanceof PsiTryStatement){
            return TRY_STATEMENT;
        }
        if(statement instanceof PsiWhileStatement){
            return WHILE_STATEMENT;
        }
        return -1;
    }
}