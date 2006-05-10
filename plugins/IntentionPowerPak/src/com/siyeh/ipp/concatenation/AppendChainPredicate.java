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
        if(!AppendUtil.isAppendCall(element)){
            return false;
        }
	    final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if(!(qualifier instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression qualifierCall = (PsiMethodCallExpression) element;
        if(!AppendUtil.isAppendCall(qualifierCall)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiExpressionStatement){
            return true;
        }
	    final PsiElement grandParent = parent.getParent();
	    if (parent instanceof PsiLocalVariable && grandParent instanceof PsiDeclarationStatement) {
		    final PsiDeclarationStatement declarationStatement =
				    (PsiDeclarationStatement)grandParent;
		    if (declarationStatement.getDeclaredElements().length == 1) {
			    return true;
		    }
	    }
	    return parent instanceof PsiAssignmentExpression &&
                grandParent instanceof PsiExpressionStatement;
    }
}
