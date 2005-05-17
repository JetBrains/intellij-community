package com.siyeh.ig.bugs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class IteratorNextDoesNotThrowNoSuchElementExceptionInspection
        extends MethodInspection{
    public String getID(){
        return "IteratorNextCanNotThrowNoSuchElementException";
    }

    public String getDisplayName(){
        return "'Iterator.next()' which can't throw NoSuchElementException";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Iterator.#ref() which can't throw NoSuchElementException #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IteratorNextDoesNotThrowNoSuchElementExceptionVisitor();
    }

    private static class IteratorNextDoesNotThrowNoSuchElementExceptionVisitor
            extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            // note: no call to super
            final String name = method.getName();
            if(!"next".equals(name)){
                return;
            }
            if(!method.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null){
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if(parameters.length != 0){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }

            if(!isIterator(aClass)){
                return;
            }
            final PsiManager psiManager = aClass.getManager();
            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();

            final Set<PsiType> exceptions =
                    ExceptionUtils.calculateExceptionsThrown(method);
            for(Object exception : exceptions){
                final PsiClassType type =
                        (PsiClassType) exception;
                if(type.equalsToText("java.util.NoSuchElementException")){
                    return;
                }
            }
            if(callsIteratorNext(method)){
                return;
            }
            registerMethodError(method);
        }
    }

    private static boolean callsIteratorNext(PsiElement method){
        final CallsIteratorNextVisitor visitor =
                new CallsIteratorNextVisitor();
        method.accept(visitor);
        return visitor.callsIteratorNext();
    }

    private static class CallsIteratorNextVisitor
            extends PsiRecursiveElementVisitor{
        private boolean doesCallIteratorNext = false;

        public void visitElement(@NotNull PsiElement element){
            if(!doesCallIteratorNext){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            if(doesCallIteratorNext){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"next".equals(methodName)){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null || args.length != 0){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(isIterator(containingClass)){
                doesCallIteratorNext = true;
            }
        }

        public boolean callsIteratorNext(){
            return doesCallIteratorNext;
        }
    }

    private static boolean isIterator(PsiClass aClass)
    {
        final String className = aClass.getQualifiedName();
        if("java.util.Iterator".equals(className)){
            return true;
        }
        final PsiManager psiManager = aClass.getManager();
        final Project project = aClass.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass iterator =
                psiManager.findClass("java.util.Iterator", scope);
        return aClass.isInheritor(iterator, true);
    }
}
