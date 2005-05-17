package com.siyeh.ig.abstraction;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class InstanceofInterfacesInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'instanceof' a concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'instanceof' concrete class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InstanceofInterfacesVisitor();
    }

    private static class InstanceofInterfacesVisitor extends BaseInspectionVisitor {

        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
            super.visitInstanceOfExpression(expression);
            final PsiTypeElement typeElement = expression.getCheckType();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            registerError(typeElement);
        }
    }

}
