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

public class VariableAssignedVisitor extends JavaRecursiveElementVisitor{

    private boolean assigned = false;
    @NotNull private final PsiVariable variable;

    public VariableAssignedVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    @Override
    public void visitClass(PsiClass aClass) {
        // do not recurse into contained classes
    }

    @Override public void visitElement(@NotNull PsiElement element){
        if(!assigned){
            super.visitElement(element);
        }
    }

    @Override public void visitAssignmentExpression(
            @NotNull PsiAssignmentExpression assignment){
        if(assigned){
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression arg = assignment.getLExpression();
        if(VariableAccessUtils.mayEvaluateToVariable(arg, variable)){
            assigned = true;
        }
    }

    @Override public void visitPrefixExpression(
            @NotNull PsiPrefixExpression prefixExpression){
        if(assigned){
            return;
        }
        super.visitPrefixExpression(prefixExpression);
        final PsiJavaToken operationSign = prefixExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                   !tokenType.equals(JavaTokenType.MINUSMINUS)){
            return;
        }
        final PsiExpression operand = prefixExpression.getOperand();
        if(VariableAccessUtils.mayEvaluateToVariable(operand, variable)){
            assigned = true;
        }
    }

    @Override public void visitPostfixExpression(
            @NotNull PsiPostfixExpression postfixExpression){
        if(assigned){
            return;
        }
        super.visitPostfixExpression(postfixExpression);
        final PsiJavaToken operationSign = postfixExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                   !tokenType.equals(JavaTokenType.MINUSMINUS)){
            return;
        }
        final PsiExpression operand = postfixExpression.getOperand();
        if(VariableAccessUtils.mayEvaluateToVariable(operand, variable)){
            assigned = true;
        }
    }

    public boolean isAssigned(){
        return assigned;
    }
}