/*
 * Copyright 2003-2006 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

class IteratorUtils {

    private IteratorUtils() {}

    public static boolean callsIteratorNext(PsiMethod method) {
        final CallsIteratorNextVisitor visitor =
                new CallsIteratorNextVisitor();
        method.accept(visitor);
        return visitor.callsIteratorNext();
    }

    public static boolean isIterator(PsiClass aClass) {
        return ClassUtils.isSubclass(aClass, "java.util.Iterator");
    }

    private static class CallsIteratorNextVisitor
            extends PsiRecursiveElementVisitor {

        private boolean doesCallIteratorNext = false;

        public void visitElement(@NotNull PsiElement element){
            if (doesCallIteratorNext) {
                return;
            }
            super.visitElement(element);
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            if(doesCallIteratorNext){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.NEXT.equals(methodName)){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if(args.length != 0){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(!isIterator(containingClass)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier != null && !(qualifier instanceof PsiThisExpression)
                    && !(qualifier instanceof PsiSuperExpression)){
                return;
            }
            doesCallIteratorNext = true;
        }

        public boolean callsIteratorNext(){
            return doesCallIteratorNext;
        }
    }
}