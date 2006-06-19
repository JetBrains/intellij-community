/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.chartostring;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class CharToStringPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression expression =
                (PsiLiteralExpression) element;
        final PsiType type = expression.getType();
        if(!PsiType.CHAR.equals(type)){
            return false;
        }
        return isInConcatenationContext(expression);
    }

    private static boolean isInConcatenationContext(PsiElement element){
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiBinaryExpression){
            final PsiBinaryExpression parentExpression =
                    (PsiBinaryExpression) parent;
            final PsiType parentType = parentExpression.getType();
            if(parentType == null){
                return false;
            }
            final String parentTypeText = parentType.getCanonicalText();
            return "java.lang.String".equals(parentTypeText);
        } else if(parent instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression parentExpression =
                    (PsiAssignmentExpression) parent;
            final PsiJavaToken sign = parentExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.PLUSEQ.equals(tokenType)){
                return false;
            }
            final PsiType parentType = parentExpression.getType();
            if(parentType == null){
                return false;
            }
            final String parentTypeText = parentType.getCanonicalText();
            return "java.lang.String".equals(parentTypeText);
        } else if(parent instanceof PsiExpressionList){
            final PsiElement grandParent = parent.getParent();
            if(!(grandParent instanceof PsiMethodCallExpression)){
                return false;
            }
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression) grandParent;
            final PsiReferenceExpression methodExpression =
                    methodCall.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            final PsiType type;
            if(qualifierExpression == null){
                // to use the intention inside the source of
                // String and StringBuffer
                type = methodExpression.getType();
            } else{
                type = qualifierExpression.getType();
            }
            if(type == null){
                return false;
            }
            final String className = type.getCanonicalText();
            if("java.lang.StringBuffer".equals(className) ||
                    "java.lang.StringBuilder".equals(className)){
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if (!"append".equals(methodName) &&
                        !"insert".equals(methodName)) {
                    return false;
                }
                final PsiElement method = methodExpression.resolve();
                return method != null;
            } else if("java.lang.String".equals(className)){
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if (!"indexOf".equals(methodName) &&
                        !"lastIndexOf".equals(methodName) &&
                        !"replace".equals(methodName)) {
                    return false;
                }
                final PsiElement method = methodExpression.resolve();
                return method != null;
            }
        }
        return false;
    }
}