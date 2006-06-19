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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;

import java.util.Set;

public class DeclarationUtils{

    private DeclarationUtils(){
        super();
    }

    public static void calculateVariablesDeclared(
            PsiStatement statement, Set<String> variablesDeclaredAtTopLevel,
            Set<String> variablesDeclaredAtLowerLevels, boolean isTopLevel){
        if(statement == null){
            return;
        }
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiExpressionStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiThrowStatement ||
                statement instanceof PsiExpressionListStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiReturnStatement){
        } else if(statement instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement declStatement =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] elements = declStatement.getDeclaredElements();
            for(PsiElement element : elements){
                final PsiVariable var = (PsiVariable) element;
                final String varName = var.getName();
                if(isTopLevel){
                    variablesDeclaredAtTopLevel.add(varName);
                } else{
                    variablesDeclaredAtLowerLevels.add(varName);
                }
            }
        } else if(statement instanceof PsiForStatement){
            final PsiForStatement loopStatement = (PsiForStatement) statement;
            final PsiStatement initialization =
                    loopStatement.getInitialization();
            calculateVariablesDeclared(initialization,
                                       variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
            final PsiStatement update = loopStatement.getUpdate();
            calculateVariablesDeclared(update, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
            final PsiStatement body = loopStatement.getBody();
            calculateVariablesDeclared(body, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
        } else if(statement instanceof PsiWhileStatement){
            final PsiWhileStatement loopStatement =
                    (PsiWhileStatement) statement;
            final PsiStatement body = loopStatement.getBody();
            calculateVariablesDeclared(body, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
        } else if(statement instanceof PsiDoWhileStatement){
            final PsiDoWhileStatement loopStatement =
                    (PsiDoWhileStatement) statement;
            final PsiStatement body = loopStatement.getBody();
            calculateVariablesDeclared(body, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiSynchronizedStatement syncStatement =
                    (PsiSynchronizedStatement) statement;
            final PsiCodeBlock body = syncStatement.getBody();
            calculateVariablesDeclaredInCodeBlock(
                    body, variablesDeclaredAtTopLevel,
                    variablesDeclaredAtLowerLevels, false);
        } else if(statement instanceof PsiBlockStatement){
            final PsiBlockStatement block = (PsiBlockStatement) statement;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            calculateVariablesDeclaredInCodeBlock(
                    codeBlock, variablesDeclaredAtTopLevel,
                    variablesDeclaredAtLowerLevels, isTopLevel);
        } else if(statement instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement =
                    (PsiLabeledStatement) statement;
            final PsiStatement body = labeledStatement.getStatement();
            calculateVariablesDeclared(body, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            calculateVariablesDeclared(thenBranch, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            calculateVariablesDeclared(elseBranch, variablesDeclaredAtTopLevel,
                                       variablesDeclaredAtLowerLevels, false);
        } else if(statement instanceof PsiTryStatement){
            final PsiTryStatement tryStatement = (PsiTryStatement) statement;
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            calculateVariablesDeclaredInCodeBlock(
                    tryBlock, variablesDeclaredAtTopLevel,
                    variablesDeclaredAtLowerLevels, false);
            calculateVariablesDeclaredInCodeBlock(
                    tryBlock, variablesDeclaredAtTopLevel,
                    variablesDeclaredAtLowerLevels, false);
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if(finallyBlock != null){
                calculateVariablesDeclaredInCodeBlock(
                        finallyBlock, variablesDeclaredAtTopLevel,
                        variablesDeclaredAtLowerLevels, false);
            }
            final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
            for(PsiCodeBlock catchBlock : catchBlocks){
                calculateVariablesDeclaredInCodeBlock(
                        catchBlock, variablesDeclaredAtTopLevel,
                        variablesDeclaredAtLowerLevels, false);
            }
        } else if(statement instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement) statement;
            final PsiCodeBlock body = switchStatement.getBody();
            calculateVariablesDeclaredInCodeBlock(
                    body, variablesDeclaredAtTopLevel,
                    variablesDeclaredAtLowerLevels, false);
        }
    }

    private static void calculateVariablesDeclaredInCodeBlock(
            PsiCodeBlock block, Set<String> variablesDeclaredAtTopLevel,
            Set<String>variablesDeclaredAtLowerLevels, boolean isTopLevel){
        if(block == null){
            return;
        }
        final PsiStatement[] statements = block.getStatements();
        for(PsiStatement statement : statements){
            calculateVariablesDeclared(
                    statement, variablesDeclaredAtTopLevel,
                    variablesDeclaredAtLowerLevels, isTopLevel);
        }
    }
}