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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

class CatchParameterUsedVisitor extends PsiRecursiveElementVisitor{

    private final PsiParameter parameter;
    private boolean used = false;

    CatchParameterUsedVisitor(PsiParameter variable){
        super();
        parameter = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!used){
            super.visitElement(element);
        }
    }

    public void visitReferenceExpression(
            @NotNull PsiReferenceExpression reference){
        if(used){
            return;
        }
        super.visitReferenceExpression(reference);
        final PsiElement element = reference.resolve();
        if(parameter.equals(element)){
            used = true;
        }
    }

    public boolean isUsed(){
        return used;
    }
}
