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
import org.jetbrains.annotations.NotNull;

public class ArrayContentsAccessedVisitor extends JavaRecursiveElementVisitor{
    private boolean accessed = false;
    private final PsiVariable variable;

    public ArrayContentsAccessedVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    @Override public void visitForeachStatement(@NotNull PsiForeachStatement statement){
        if(accessed){
            return;
        }
        super.visitForeachStatement(statement);
        final PsiExpression qualifier = statement.getIteratedValue();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) qualifier).resolve();
        if(referent == null){
            return;
        }
        if(!referent.equals(variable)){
            return;
        }
        accessed = true;
    }

    @Override public void visitArrayAccessExpression(PsiArrayAccessExpression arg){
        if(accessed){
            return;
        }
        super.visitArrayAccessExpression(arg);
        if(arg.getParent() instanceof PsiAssignmentExpression &&
                        ((PsiAssignmentExpression) arg.getParent()).getLExpression()
                                .equals(arg)){
            return;
        }
        final PsiExpression arrayExpression = arg.getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            accessed = true;
        }
    }

    public boolean isAccessed(){
        return accessed;
    }
}
