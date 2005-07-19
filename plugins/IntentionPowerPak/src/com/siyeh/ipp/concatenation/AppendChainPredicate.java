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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class AppendChainPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        if(!AppendUtil.isAppend(call)){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if(!(qualifier instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression qualifierCall = (PsiMethodCallExpression) element;
        if(!AppendUtil.isAppend(qualifierCall)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiExpressionStatement){
            return true;
        }
        if(parent instanceof PsiLocalVariable &&
                parent.getParent() instanceof PsiDeclarationStatement &&
                ((PsiDeclarationStatement) parent.getParent())
                        .getDeclaredElements().length == 1){
            return true;
        }
        return parent instanceof PsiAssignmentExpression &&
                parent.getParent() instanceof PsiExpressionStatement;
    }
}
