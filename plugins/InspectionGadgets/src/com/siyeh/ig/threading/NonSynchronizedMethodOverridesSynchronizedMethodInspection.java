package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class NonSynchronizedMethodOverridesSynchronizedMethodInspection extends MethodInspection {

    public String getDisplayName() {
        return "Non-synchronized method overrides synchronized method";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-synchronized method '#ref' overrides synchronized method #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NonSynchronizedMethodOverridesSynchronizedMethodVisitor(this, inspectionManager, onTheFly);
    }

    private static class NonSynchronizedMethodOverridesSynchronizedMethodVisitor extends BaseInspectionVisitor {
        private NonSynchronizedMethodOverridesSynchronizedMethodVisitor(BaseInspection inspection,
                                                                        InspectionManager inspectionManager,
                                                                        boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (method.isConstructor()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                return;
            }
            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
            for(final PsiMethod superMethod : superMethods){
                if(superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
                    registerMethodError(method);
                    return;
                }
            }
        }

    }

}
