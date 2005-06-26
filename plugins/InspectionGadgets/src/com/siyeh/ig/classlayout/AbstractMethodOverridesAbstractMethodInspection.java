package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

public class AbstractMethodOverridesAbstractMethodInspection extends MethodInspection {
    private final AbstractMethodOverridesAbstractMethodFix fix = new AbstractMethodOverridesAbstractMethodFix();

    public String getDisplayName() {
        return "Abstract method overrides abstract method";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Abstract method '#ref' overrides abstract method #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class  AbstractMethodOverridesAbstractMethodFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove redundant abstract method declaration";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiElement method = methodNameIdentifier.getParent();
            assert method!=null;
            deleteElement(method);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AbstractMethodOverridesAbstractMethodVisitor();
    }

    private static class AbstractMethodOverridesAbstractMethodVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (method.isConstructor()) {
                return;
            }
            if(!isAbstract(method))
            {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.ABSTRACT) &&
                    !containingClass.isInterface()) {
                return;
            }
            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
            for(final PsiMethod superMethod : superMethods){
                if(isAbstract(superMethod)){
                    if(methodsHaveSameReturnTypes(method, superMethod) &&
                            haveSameExceptionSignatures(method, superMethod))
                    {
                        registerMethodError(method);
                        return;
                    }
                }
            }
        }

        private static boolean haveSameExceptionSignatures(PsiMethod method1,
                                                    PsiMethod method2){
            final PsiReferenceList list1 = method1.getThrowsList();
            final PsiClassType[] exceptions1 = list1.getReferencedTypes();
            final PsiReferenceList list2 = method2.getThrowsList();
            final PsiClassType[] exceptions2 = list2.getReferencedTypes();
            if(exceptions1.length !=exceptions2.length)
            {
                return false;
            }
            final Set<PsiClassType> set1 = new HashSet<PsiClassType>();
            Collections.addAll(set1, exceptions1);
            for(PsiClassType anException : exceptions2){
                if(!set1.contains(anException)){
                    return false;
                }
            }
            return true;
        }

        private static boolean methodsHaveSameReturnTypes(PsiMethod method1,
                                                   PsiMethod method2){
            final PsiType type1 = method1.getReturnType();
            if(type1 == null)
            {
                return false;
            }
            final PsiType type2 = method2.getReturnType();
            if(type2 == null)
            {
                return false;
            }
            return type1.equals(type2);
        }

        private static boolean isAbstract(PsiMethod method){
            final PsiClass containingClass = method.getContainingClass();
            if(method.hasModifierProperty(PsiModifier.ABSTRACT)){
                return true;
            }
            if(containingClass == null){
                return false;
            }
            return containingClass.isInterface();
        }
    }
}
