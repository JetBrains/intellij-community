package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class EqualsUsesNonFinalVariableInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Non-final field referenced in 'equals()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-final field #ref accessed in equals()  #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new EqualsUsesNonFinalVariableVisitor(this, inspectionManager, onTheFly);
    }

    private static class EqualsUsesNonFinalVariableVisitor extends BaseInspectionVisitor {
        private boolean m_inEquals = false;

        private EqualsUsesNonFinalVariableVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (!m_inEquals) {
                return;
            }
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) element;
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerError(expression);
        }

        public void visitMethod(PsiMethod method) {
            final boolean isEquals = isEqualsMethod(method);
            if (isEquals) {
                m_inEquals = true;
            }

            super.visitMethod(method);
            if (isEquals) {
                m_inEquals = false;
            }
        }

        private static boolean isEqualsMethod(PsiMethod method) {
            final String methodName = method.getName();
            if (!"equals".equals(methodName)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null || parameters.length != 1) {
                return false;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return false;
            }
            if (!returnType.equals(PsiType.BOOLEAN)) {
                return false;
            }
            return true;
        }

    }

}
