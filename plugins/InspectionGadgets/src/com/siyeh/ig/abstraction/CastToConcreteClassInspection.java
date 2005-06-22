package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class CastToConcreteClassInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Cast to a concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Cast to concrete class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CastToConcreteClassVisitor();
    }

    private static class CastToConcreteClassVisitor extends BaseInspectionVisitor {

        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiTypeElement typeElement = expression.getCastType();

            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            registerError(typeElement);
        }
    }
}
