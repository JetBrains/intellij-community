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
import org.jetbrains.annotations.Nullable;

class ConstantSubexpressionPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken) &&
                !(element.getPrevSibling() instanceof PsiJavaToken)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiBinaryExpression)){
            return false;
        }
	    final PsiBinaryExpression binaryExpression =
			    (PsiBinaryExpression) parent;
	    final PsiType type = binaryExpression.getType();
	    if(type == null || type.equalsToText("java.lang.String")){
		    // handled by JoinConcatenatedStringLiteralsIntention
		    return false;
	    }
        final PsiBinaryExpression subexpression =
		        getSubexpression(binaryExpression);
        if(subexpression == null){
	        return false;
        }
        if(binaryExpression.equals(subexpression) &&
                !isPartOfConstantExpression(binaryExpression)){
            // handled by ConstantExpressonIntention
            return false;
        }
        if(!PsiUtil.isConstantExpression(subexpression)){
            return false;
        }
        final PsiManager manager = element.getManager();
        final PsiConstantEvaluationHelper helper =
                manager.getConstantEvaluationHelper();
        final Object value = helper.computeConstantExpression(subexpression);
        return value != null;
    }

    private static boolean isPartOfConstantExpression(
		    PsiBinaryExpression binaryExpression){
        final PsiElement containingElement = binaryExpression.getParent();
        if(containingElement instanceof PsiExpression){
            final PsiExpression containingExpression =
		            (PsiExpression) containingElement;
            if(!PsiUtil.isConstantExpression(containingExpression)){
                return false;
            }
        } else{
            return false;
        }
        return true;
    }

    /**
     * Returns the smallest subexpression (if precendence allows it). example:
     * variable + 2 + 3 normally gets evaluated left to right -> (variable + 2)
     * + 3 this method returns the right most legal subexpression -> 2 + 3
     */
    @Nullable
    private static PsiBinaryExpression getSubexpression(
		    PsiBinaryExpression expression){
        final PsiExpression rhs = expression.getROperand();
	    if(rhs == null){
		    return null;
	    }
	    final PsiExpression lhs = expression.getLOperand();
        if(!(lhs instanceof PsiBinaryExpression)){
            return expression;
        }
        final PsiBinaryExpression lhsBinaryExpression =
		        (PsiBinaryExpression) lhs;
        final PsiExpression leftSide = lhsBinaryExpression.getROperand();
        if(leftSide == null){
	        return null;
        }
        try{
	        final PsiBinaryExpression binaryExpression =
			        (PsiBinaryExpression) expression.copy();
	        final PsiExpression lOperand = binaryExpression.getLOperand();
            lOperand.replace(leftSide);
	        return binaryExpression;
        } catch(Throwable ignore){
            return null;
        }
    }
}