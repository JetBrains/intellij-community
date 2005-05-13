package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class AbstractMethodOverridesConcreteMethodInspection extends MethodInspection {

    public String getDisplayName() {
        return "Abstract method overrides concrete method";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Abstract method '#ref' overrides concrete method #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AbstractMethodOverridesConcreteMethodVisitor(this, inspectionManager, onTheFly);
    }

    private static class AbstractMethodOverridesConcreteMethodVisitor extends BaseInspectionVisitor {
        private AbstractMethodOverridesConcreteMethodVisitor(BaseInspection inspection,
                                                             InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (method.isConstructor()) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
            for(final PsiMethod superMethod : superMethods){
                final PsiClass superClass = superMethod.getContainingClass();
                final String superClassName = superClass.getQualifiedName();
                if(!superClass.isInterface() &&
                        !"java.lang.Object".equals(superClassName) &&
                        !superMethod.hasModifierProperty(PsiModifier.ABSTRACT)){
                    registerMethodError(method);
                    return;
                }
            }
        }
    }
}
