package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class AccessToStaticFieldLockedOnInstanceInspection extends ExpressionInspection {

    private static final Logger logger =
            Logger.getInstance("#AccessToStaticFieldLockedOnInstanceInspection");

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "access.to.static.field.locked.on.instance.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor
            extends BaseInspectionVisitor {


        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            boolean isLockedOnInstance = false;
            boolean isLockedOnClass = false;
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if (containingMethod != null) {

                if (containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                    if (containingMethod.hasModifierProperty(PsiModifier.STATIC)) {
                        isLockedOnClass = true;
                    } else {
                        isLockedOnInstance = true;
                    }
                }
            }

            PsiElement elementToCheck = expression;
            while (elementToCheck != null) {
                final PsiSynchronizedStatement syncStatement =
                        PsiTreeUtil.getParentOfType(elementToCheck, PsiSynchronizedStatement.class);
                if (syncStatement != null) {
                    final PsiExpression lockExpression = syncStatement.getLockExpression();
                    if (lockExpression instanceof PsiReferenceExpression) {
                        final PsiReferenceExpression reference = (PsiReferenceExpression) lockExpression;
                        final PsiElement referent = reference.resolve();
                        if (referent instanceof PsiField) {
                            final PsiField referentField = (PsiField) referent;
                            if (referentField.hasModifierProperty(PsiModifier.STATIC)) {
                                isLockedOnClass = true;
                            } else {
                                isLockedOnInstance = true;
                            }
                        }
                    } else if (lockExpression instanceof PsiThisExpression) {
                        isLockedOnInstance = true;
                    } else if (lockExpression instanceof PsiClassObjectAccessExpression) {
                        isLockedOnClass = true;
                    }
                }
                elementToCheck = syncStatement;
            }

            if (isLockedOnInstance && !isLockedOnClass) {
                final PsiElement referent = expression.resolve();
                if (referent instanceof PsiField) {
                    final PsiField referredField = (PsiField) referent;
                    if (referredField.hasModifierProperty(PsiModifier.STATIC) &&
                            !isConstant(referredField)) {
                        registerError(expression);
                    }
                }
            }
        }
    }

    private static boolean isConstant(PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        final PsiType type = field.getType();
        return ClassUtils.isImmutable(type);
    }
}
