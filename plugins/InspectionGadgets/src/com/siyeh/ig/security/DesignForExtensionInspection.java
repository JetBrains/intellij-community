package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class DesignForExtensionInspection extends MethodInspection {

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "design.for.extension.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DesignForExtensionVisitor();
    }

    private static class DesignForExtensionVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if(method.isConstructor())
            {
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE)||
                    method.hasModifierProperty(PsiModifier.FINAL) ||
                    method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.hasModifierProperty(PsiModifier.STATIC))
            {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass==null)
            {
                return;
            }
            if(containingClass.hasModifierProperty(PsiModifier.FINAL))
            {
                return;
            }
            if(containingClass.getName()==null)
            {
               return; //anonymous classes can't be overridden
            }
            final PsiCodeBlock body = method.getBody();
            if(body == null)
            {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if(statements.length!=0)
            {
                return;
            }
            registerMethodError(method);
        }

    }
}
