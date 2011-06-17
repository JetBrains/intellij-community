/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;

class AppendUtil{

    private AppendUtil(){}

    public static boolean isAppendCall(PsiElement element){
        if (!(element instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression)element;
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        @NonNls final String callName = methodExpression.getReferenceName();
        if(!"append".equals(callName)){
            return false;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        final PsiClass containingClass;
        if(method == null){
            // if the argument has no type because of invalid code
            // this uses the qualifier as type, so the conversion too
            // append sequence is still applicable
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (qualifierExpression == null) {
                return false;
            }
            final PsiType type = qualifierExpression.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType) type;
            containingClass = classType.resolve();
        } else {
            containingClass = method.getContainingClass();
        }
        if(containingClass == null){
            return false;
        }
        final String name = containingClass.getQualifiedName();
        if ("java.lang.StringBuffer".equals(name) ||
                "java.lang.StringBuilder".equals(name)) {
            return true;
        }
        final Project project = containingClass.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass appendableClass =
                psiFacade.findClass("java.lang.Appendable",
                        GlobalSearchScope.allScope(project));
        if (appendableClass == null) {
            return false;
        }
        return containingClass.isInheritor(appendableClass, true);
    }
}
