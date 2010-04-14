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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class VariablePassedAsArgumentVisitor extends JavaRecursiveElementVisitor{

    @NotNull
    private final PsiVariable variable;
    private boolean passed = false;

    public VariablePassedAsArgumentVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    @Override public void visitElement(@NotNull PsiElement element){
        if(!passed){
            super.visitElement(element);
        }
    }

    @Override public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression call){
        if(passed){
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        for(PsiExpression argument : arguments){
            if(VariableAccessUtils.mayEvaluateToVariable(argument, variable)){
                passed = true;
            }
        }
    }

    @Override public void visitNewExpression(
            @NotNull PsiNewExpression newExpression){
        if(passed){
            return;
        }
        super.visitNewExpression(newExpression);
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList == null){
            return;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        for(PsiExpression argument : arguments){
            if(VariableAccessUtils.mayEvaluateToVariable(argument, variable)){
                passed = true;
            }
        }
    }

    public boolean isPassed(){
        return passed;
    }
}