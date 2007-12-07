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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ArrayContentsAssignedVisitor extends JavaRecursiveElementVisitor {
    private boolean assigned = false;
    private final PsiVariable variable;

    public ArrayContentsAssignedVisitor(@NotNull PsiVariable variable) {
        super();
        this.variable = variable;
    }

    @Override public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment){
        if(assigned)
        {
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression arg = assignment.getLExpression();
        if(!(arg instanceof PsiArrayAccessExpression)){
            return;
        }
        PsiExpression arrayExpression =
                ((PsiArrayAccessExpression) arg).getArrayExpression();
        while (arrayExpression instanceof PsiArrayAccessExpression) {
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)arrayExpression;
            arrayExpression = arrayAccessExpression.getArrayExpression();
        }
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assigned = true;
        }
    }

    @Override public void visitPrefixExpression(@NotNull PsiPrefixExpression expression){
        super.visitPrefixExpression(expression);
        final PsiJavaToken operationSign = expression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!(tokenType.equals(JavaTokenType.PLUSPLUS) ||
                tokenType.equals(JavaTokenType.MINUSMINUS))){
            return;
        }
        final PsiExpression arg = expression.getOperand();
        if(!(arg instanceof PsiArrayAccessExpression)){
            return;
        }
        final PsiExpression arrayExpression =
                ((PsiArrayAccessExpression) arg).getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assigned = true;
        }
    }
    @Override public void visitPostfixExpression(@NotNull PsiPostfixExpression expression){
        super.visitPostfixExpression(expression);
        final PsiJavaToken operationSign = expression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!(tokenType.equals(JavaTokenType.PLUSPLUS) ||
                tokenType.equals(JavaTokenType.MINUSMINUS))){
            return;
        }
        final PsiExpression arg = expression.getOperand();
        if(!(arg instanceof PsiArrayAccessExpression)){
            return;
        }
        final PsiExpression arrayExpression =
                ((PsiArrayAccessExpression) arg).getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assigned = true;
        }
    }

    public boolean isAssigned() {
        return assigned;
    }
}
