package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CastToConcreteClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class CastToConcreteClassVisitor extends BaseInspectionVisitor {

        private CastToConcreteClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiTypeElement typeElement = expression.getCastType();

            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            registerError(typeElement);
        }
    }
}
