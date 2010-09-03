/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.finalization;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

class CallToSuperFinalizeVisitor extends JavaRecursiveElementVisitor{

    private boolean callToSuperFinalizeFound = false;

    @Override public void visitElement(@NotNull PsiElement element){
        if(!callToSuperFinalizeFound){
            super.visitElement(element);
        }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
        final PsiExpression condition = statement.getCondition();
        final Object result =
                    ExpressionUtils.computeConstantExpression(condition);
        if(result != null && result.equals(Boolean.FALSE)){
            return;
        }
        super.visitIfStatement(statement);
    }

    @Override public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression expression){
        if(callToSuperFinalizeFound){
            return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiExpression target = methodExpression.getQualifierExpression();
        if(!(target instanceof PsiSuperExpression)){
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!HardcodedMethodConstants.FINALIZE.equals(methodName)){
            return;
        }
        callToSuperFinalizeFound = true;
    }

    public boolean isCallToSuperFinalizeFound(){
        return callToSuperFinalizeFound;
    }
}