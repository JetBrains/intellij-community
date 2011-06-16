package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This inspection finds instances of null checks followed by an instanceof check
 * on the same variable. For instance:
 * <code>
 *     if (x != null && x instanceof String) { ... }
 * </code>
 * The instanceof operator returns false when passed a null, so the null check is pointless.
 *
 * @author Lars Fischer
 * @author Etienne Studer
 * @author Hamlet D'Arcy
 */
public class PointlessNullCheckInspection extends BaseInspection {
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("pointless.nullcheck.display.name");
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message("pointless.nullcheck.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PointlessNullCheckVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new PointlessNullCheckFix();
    }

    private static class PointlessNullCheckFix extends InspectionGadgetsFix {

        @Override
        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "pointless.nullcheck.simplify.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) parent;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (lhs instanceof PsiInstanceOfExpression) {
                replaceExpression(binaryExpression, lhs.getText());
            } else if (rhs instanceof PsiInstanceOfExpression) {
                replaceExpression(binaryExpression, rhs.getText());
            }
        }
    }

    private static class PointlessNullCheckVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (!expression.getOperationTokenType().equals(
                    JavaTokenType.ANDAND)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();

            final PsiBinaryExpression binaryExpression;
            final PsiInstanceOfExpression instanceofExpression;
            if (lhs instanceof PsiBinaryExpression &&
                    rhs instanceof PsiInstanceOfExpression) {
                binaryExpression = (PsiBinaryExpression) lhs;
                instanceofExpression = (PsiInstanceOfExpression) rhs;
            } else if (rhs instanceof PsiBinaryExpression &&
                    lhs instanceof PsiInstanceOfExpression) {
                binaryExpression = (PsiBinaryExpression) rhs;
                instanceofExpression = (PsiInstanceOfExpression) lhs;
            } else {
                return;
            }
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.NE)) {
                return;
            }
            final PsiReferenceExpression referenceExpression1 =
                    getReferenceFromNotNullCheck(binaryExpression);
            if (referenceExpression1 == null) {
                return;
            }
            final PsiExpression operand =
                    instanceofExpression.getOperand();
            if (!(operand instanceof PsiReferenceExpression)) {
                return;
            }

            final PsiReferenceExpression referenceExpression2 =
                    (PsiReferenceExpression) operand;
            final PsiElement target1 = referenceExpression1.resolve();
            final PsiElement target2 = referenceExpression2.resolve();
            if (target1 == null || !target1.equals(target2)) {
                return;
            }
            registerError(binaryExpression);
        }

        @Nullable
        private static PsiReferenceExpression getReferenceFromNotNullCheck(
                PsiBinaryExpression expression) {
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (lhs instanceof PsiReferenceExpression) {
                if (!(rhs instanceof PsiLiteralExpression &&
                        PsiType.NULL.equals(rhs.getType()))) {
                    return null;
                }
                return (PsiReferenceExpression) lhs;
            } else if (rhs instanceof PsiReferenceExpression) {
                if (!(lhs instanceof PsiLiteralExpression &&
                        PsiType.NULL.equals(lhs.getType()))) {
                    return null;
                }
                return (PsiReferenceExpression) rhs;
            } else {
                return null;
            }
        }
    }
}
