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
package com.siyeh.ipp.junit;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class CreateAssertPredicate implements PsiElementPredicate{
    CreateAssertPredicate(){
        super();
    }

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiExpressionStatement)){
            return false;
        }

        final PsiExpressionStatement statement =
                (PsiExpressionStatement) element;
        final PsiExpression expression = statement.getExpression();
        final PsiElement parent = expression.getParent();
        if(!(parent instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiType type = expression.getType();
        if(!PsiType.BOOLEAN.equals(type)){
            return false;
        }
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
                expression, PsiMethod.class);
        if (containingMethod != null && AnnotationUtil.isAnnotated(containingMethod, "org.junit.Test",
                                       true)) {
            return true;
        }
        final PsiClass containingClass =
                PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if(!isTest(containingClass)){
            return false;
        }
        return isTestMethod(containingMethod);
    }

    private boolean isTestMethod(PsiMethod method){
        if(method == null){
            return false;
        }
        if(method.hasModifierProperty(PsiModifier.ABSTRACT) ||
           !method.hasModifierProperty(PsiModifier.PUBLIC)){
            return false;
        }

        final PsiType returnType = method.getReturnType();
        if(returnType == null){
            return false;
        }
        if(!returnType.equals(PsiType.VOID)){
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if(parameters == null){
            return false;
        }
        if(parameters.length != 0){
            return false;
        }
        @NonNls final String methodName = method.getName();
        return methodName.startsWith("test");
    }

    private static boolean isTest(PsiClass aClass){
        if(aClass == null){
            return false;
        }
        final PsiManager psiManager = aClass.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass ancestorClass =
                psiManager.findClass("junit.framework.TestCase", scope);
        return InheritanceUtil.isInheritorOrSelf(aClass, ancestorClass, true);
    }
}
