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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class VariableIsModifiedVisitor extends PsiRecursiveElementVisitor{
    private boolean modified = false;
    private final PsiVariable variable;

    VariableIsModifiedVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!modified){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
        if(modified){
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        if(method == null){
            return;
        }
        final PsiType returnType = method.getReturnType();
        if(returnType == null){
            return;
        }
        final String canonicalText = returnType.getCanonicalText();
        if(!"java.lang.StringBuffer".equals(canonicalText) &&
                   !"java.lang.StringBuilder".equals(canonicalText)){
            return;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return;
        }
        final PsiReferenceExpression reference =
                (PsiReferenceExpression) qualifier;
        final PsiElement referent = reference.resolve();
        if(variable.equals(referent)){
            modified = true;
        }
    }

    public boolean isModified(){
        return modified;
    }
}