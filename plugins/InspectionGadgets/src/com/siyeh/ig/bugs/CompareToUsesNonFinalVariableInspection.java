package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;

public class CompareToUsesNonFinalVariableInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Non-final field referenced in 'compareTo()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-final field #ref accessed in compareTo() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CompareToUsesNonFinalVariableVisitor(this, inspectionManager, onTheFly);
    }

    private static class CompareToUsesNonFinalVariableVisitor extends BaseInspectionVisitor {
        private boolean m_inCompareTo = false;

        private CompareToUsesNonFinalVariableVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (!m_inCompareTo) {
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
            final boolean isCompareTo = isCompareTo(method);
            if (isCompareTo) {
                m_inCompareTo = true;
            }

            super.visitMethod(method);
            if (isCompareTo) {
                m_inCompareTo = false;
            }
        }

        private static boolean isCompareTo(PsiMethod method) {
            final String methodName = method.getName();
            if (!"compareTo".equals(methodName)) {
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
            return TypeUtils.typeEquals("int", returnType);
        }

    }

}
