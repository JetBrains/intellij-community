package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ExceptionUtils;

import java.util.Iterator;
import java.util.Set;

public class IteratorNextDoesNotThrowNoSuchElementExceptionInspection
        extends MethodInspection{
    public String getDisplayName(){
        return "Iterator.next() which can't throw NoSuchElementException";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Iterator.#ref() which can't throw NoSuchElementException #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new IteratorNextDoesNotThrowNoSuchElementExceptionVisitor(this,
                                                                         inspectionManager,
                                                                         onTheFly);
    }

    private static class IteratorNextDoesNotThrowNoSuchElementExceptionVisitor
            extends BaseInspectionVisitor{
        private IteratorNextDoesNotThrowNoSuchElementExceptionVisitor(BaseInspection inspection,
                                                                      InspectionManager inspectionManager,
                                                                      boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
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

            final Set exceptions =
                    ExceptionUtils.calculateExceptionsThrown(method,
                                                             elementFactory);
            for(Iterator exceptionsThrown = exceptions.iterator();
                exceptionsThrown.hasNext();){
                final PsiClassType type =
                        (PsiClassType) exceptionsThrown.next();
                final String typeName = type.getCanonicalText();
                if("java.util.NoSuchElementException".equals(typeName)){
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

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
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

    public static boolean isIterator(PsiClass aClass)
    {
        final String className = aClass.getQualifiedName();
        if("java.util.Iterator".equals(className))
        {
            return true;
        }
        final PsiManager psiManager = aClass.getManager();
        final Project project = aClass.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass iterator =
                psiManager.findClass("java.util.Iterator", scope);
        return aClass.isInheritor(iterator, false);
    }
}
