package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.openapi.project.Project;
import com.siyeh.ipp.base.PsiElementPredicate;

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
        if(!(parent instanceof PsiExpressionStatement))
        {
            return false;
        }
        final PsiType type = expression.getType();
        if(!PsiType.BOOLEAN.equals(type))
        {
            return false;
        }
        final PsiClass containingClass =
                (PsiClass) PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if(!isTest(containingClass))
        {
            return false;
        }

        final PsiMethod containingMethod =
                (PsiMethod) PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        return isTestMethod(containingMethod);

    }

    private boolean isTestMethod(PsiMethod method){
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
        if(parameterList == null){
            return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        if(parameters == null){
            return false;
        }
        if(parameters.length != 0){
            return false;
        }
        final String methodName = method.getName();
        return methodName.startsWith("test");
    }

    public static boolean isTest(PsiClass aClass){
        final PsiManager psiManager = aClass.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass ancestorClass =
                psiManager.findClass("junit.framework.TestCase", scope);
        return InheritanceUtil.isInheritorOrSelf(aClass, ancestorClass, true);
    }

}
