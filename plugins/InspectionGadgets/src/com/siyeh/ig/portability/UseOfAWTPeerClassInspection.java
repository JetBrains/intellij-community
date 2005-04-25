package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.ClassUtils;

public class UseOfAWTPeerClassInspection extends VariableInspection {

    public String getDisplayName() {
        return "Use of AWT peer class";
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Use of AWT peer class #ref is non-portable #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UseOfAWTPeerClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class UseOfAWTPeerClassVisitor extends BaseInspectionVisitor {
        private UseOfAWTPeerClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }

            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiType deepComponentType = type.getDeepComponentType();
            if (deepComponentType == null) {
                return;
            }
            if(!(deepComponentType instanceof PsiClassType)) {
                return;
            }
            final PsiClass resolveClass = ((PsiClassType) deepComponentType).resolve();
            if(resolveClass == null)
            {
                return;
            }
            if(resolveClass.isEnum()||resolveClass.isInterface() || resolveClass.isAnnotationType())
            {
                return;
            }
            if(!ClassUtils.isSubclass(resolveClass, "java.awt.peer.ComponentPeer"))
            {
                return;
            }

            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitNewExpression(PsiNewExpression newExpression) {
            super.visitNewExpression(newExpression);
            final PsiType type = newExpression.getType();
            if (type == null) {
                return;
            }
            if(!(type instanceof PsiClassType))
            {
                return;
            }
            final PsiClass resolveClass = ((PsiClassType) type).resolve();
            if(resolveClass == null) {
                return;
            }
            if(resolveClass.isEnum() || resolveClass.isInterface() ||
                    resolveClass.isAnnotationType()) {
                return;
            }
            if(!ClassUtils.isSubclass(resolveClass, "java.awt.peer.ComponentPeer")) {
                return;
            }
            final PsiJavaCodeReferenceElement classNameElement = newExpression.getClassReference();
            registerError(classNameElement);
        }

    }

}
