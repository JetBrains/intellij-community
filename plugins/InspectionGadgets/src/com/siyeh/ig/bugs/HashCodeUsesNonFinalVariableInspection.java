package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class HashCodeUsesNonFinalVariableInspection extends ExpressionInspection {
    public String getID(){
        return "NonFinalFieldReferencedInHashCode";
    }
    public String getDisplayName() {
        return "Non-final field referenced in 'hashCode()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }


    public String buildErrorString(PsiElement location) {
        return "Non-final field #ref accessed in hashCode()  #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new HashCodeUsesNonFinalVariableVisitor();
    }

    private static class HashCodeUsesNonFinalVariableVisitor extends BaseInspectionVisitor {
        private boolean m_inHashcode = false;

        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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

        public void visitMethod(@NotNull PsiMethod method) {
            final boolean isHashCode = MethodUtils.isHashCode(method);
            if (isHashCode) {
                m_inHashcode = true;
            }

            super.visitMethod(method);
            if (isHashCode) {
                m_inHashcode = false;
            }
        }

    }

}
