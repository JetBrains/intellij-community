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

import java.util.Set;

class VariablePassedAsArgumentExcludedVisitor
        extends JavaRecursiveElementVisitor{

    @NotNull
    private final PsiVariable variable;
    private final Set<String> excludes;
    private boolean passed = false;

    public VariablePassedAsArgumentExcludedVisitor(
            @NotNull PsiVariable variable, @NotNull Set<String> excludes) {
        super();
        this.variable = variable;
        this.excludes = excludes;
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
            if(!VariableAccessUtils.mayEvaluateToVariable(argument, variable)){
                continue;
            }
            final PsiMethod method = call.resolveMethod();
            if(method != null){
                final PsiClass aClass = method.getContainingClass();
                if(aClass != null){
                    final String name = aClass.getQualifiedName();
                    if(excludes.contains(name)){
                        continue;
                    }
                }
            }
            passed = true;
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
            if(!VariableAccessUtils.mayEvaluateToVariable(argument, variable)){
                continue;
            }
            final PsiMethod constructor = newExpression.resolveConstructor();
            if(constructor != null){
                final PsiClass aClass = constructor.getContainingClass();
                if(aClass != null){
                    final String name = aClass.getQualifiedName();
                    if(excludes.contains(name)){
                        continue;
                    }
                }
            }
            passed = true;
        }
    }

    public boolean isPassed(){
        return passed;
    }
}