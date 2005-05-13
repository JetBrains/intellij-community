package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class CloneCallsConstructorsInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'clone()' instantiates objects with constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "clone() instantiates objects with constructor new #ref() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CloneCallsConstructorVisitor(this, inspectionManager, onTheFly);
    }

    private static class CloneCallsConstructorVisitor extends BaseInspectionVisitor {
        private boolean m_inClone = false;

        private CloneCallsConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            boolean wasInClone = m_inClone;
            final String methodName = method.getName();
            final PsiParameterList parameterList = method.getParameterList();
            final boolean isClone = "clone".equals(methodName) &&
                    parameterList.getParameters().length == 0;
            if (isClone) {
                wasInClone = m_inClone;
                m_inClone = true;
            }
            super.visitMethod(method);
            if (isClone) {
                m_inClone = wasInClone;
            }
        }

        public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
            super.visitNewExpression(newExpression);
            if (!m_inClone) {
                return;
            }

            final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
            if (arrayDimensions != null && arrayDimensions.length != 0) {
                return;
            }
            if (newExpression.getArrayInitializer() != null) {
                return;
            }
            if (newExpression.getAnonymousClass() != null) {
                return;
            }
            if (isPartOfThrowStatement(newExpression)) {
                return;
            }

            final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            registerError(classReference);
        }

        private static boolean isPartOfThrowStatement(PsiElement element) {
            return PsiTreeUtil.getParentOfType(element, PsiThrowStatement.class) != null;
        }

    }

}
