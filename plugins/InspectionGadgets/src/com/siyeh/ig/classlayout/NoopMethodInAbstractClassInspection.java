package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class NoopMethodInAbstractClassInspection extends MethodInspection {

    public String getDisplayName() {
        return "No-op method in abstract class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "No-op Method '#ref' should be made abstract #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NoopMethodInAbstractClassVisitor();
    }

    private static class NoopMethodInAbstractClassVisitor extends BaseInspectionVisitor {
     
        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (method.isConstructor()) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return;
            }
            if (statements.length > 0) {
                return;
            }
            registerMethodError(method);
        }
    }
}
