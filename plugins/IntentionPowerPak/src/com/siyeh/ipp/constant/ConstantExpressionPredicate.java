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
package com.siyeh.ipp.constant;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConstantExpressionPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiExpression)){
            return false;
        }
        if(element instanceof PsiLiteralExpression ||
                element instanceof PsiClassObjectAccessExpression){
	        return false;
        }
	    final PsiExpression expression = (PsiExpression) element;
	    final PsiType type = expression.getType();
	    if (type == null || type.equalsToText("java.lang.String")){
		    return false;
	    }
        if(!PsiUtil.isConstantExpression(expression)){
            return false;
        }
        final PsiManager manager= element.getManager();
        final PsiConstantEvaluationHelper helper =
                manager.getConstantEvaluationHelper();
        final Object value = helper.computeConstantExpression(expression);
        if(value == null){
	        return false;
        }
        final PsiElement parent = element.getParent();
	    return !(parent instanceof PsiExpression &&
			    PsiUtil.isConstantExpression((PsiExpression)parent));
    }
}
