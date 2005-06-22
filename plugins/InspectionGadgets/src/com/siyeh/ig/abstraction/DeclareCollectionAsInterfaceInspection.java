package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class DeclareCollectionAsInterfaceInspection extends VariableInspection {

    public String getID(){
        return "CollectionDeclaredAsConcreteClass";
    }

    public String getDisplayName() {
        return "Collection declared by class, not interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String type = location.getText();
        final String interfaceName = CollectionUtils.getInterfaceForClass(type);
        return "Declaration of #ref should probably be weakened to " + interfaceName + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DeclareCollectionAsInterfaceVisitor();
    }

    private static class DeclareCollectionAsInterfaceVisitor extends BaseInspectionVisitor {

        public void visitVariable(@NotNull PsiVariable variable) {
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final PsiType type = method.getReturnType();
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            registerError(typeElement);
        }

    }

}
