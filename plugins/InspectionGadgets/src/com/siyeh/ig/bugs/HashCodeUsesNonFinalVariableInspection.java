package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class HashCodeUsesNonFinalVariableInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Non-final field referenced in 'hashCode()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-final field #ref accessed in hashCode()  #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new HashCodeUsesNonFinalVariableVisitor(this, inspectionManager, onTheFly);
    }

    private static class HashCodeUsesNonFinalVariableVisitor extends BaseInspectionVisitor {
        private boolean m_inHashcode = false;

        private HashCodeUsesNonFinalVariableVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (!m_inHashcode) {
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
            final boolean isHashCode = isHashCode(method);
            if (isHashCode) {
                m_inHashcode = true;
            }

            super.visitMethod(method);
            if (isHashCode) {
                m_inHashcode = false;
            }
        }

        private static boolean isHashCode(PsiMethod method) {
            final String methodName = method.getName();
            if (!"hashCode".equals(methodName)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null || parameters.length != 0) {
                return false;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return false;
            }
            if (!returnType.equals(PsiType.INT)) {
                return false;
            }
            return true;
        }

    }

}
