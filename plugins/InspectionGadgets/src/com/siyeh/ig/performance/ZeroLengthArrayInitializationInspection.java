package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import org.jetbrains.annotations.NotNull;

public class ZeroLengthArrayInitializationInspection extends ExpressionInspection {
    private final IntroduceConstantFix fix = new IntroduceConstantFix();

    public String getID(){
        return "ZeroLengthArrayAllocation";
    }

    public String getDisplayName() {
        return "Zero-length array allocation";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Allocation of zero length array #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ZeroLengthArrayInitializationVisitor(this, inspectionManager, onTheFly);
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class ZeroLengthArrayInitializationVisitor extends BaseInspectionVisitor {
        private ZeroLengthArrayInitializationVisitor(BaseInspection inspection,
                                                     InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiExpression[] dimensions = expression.getArrayDimensions();
            final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
            if (arrayInitializer != null) {
                final PsiExpression[] initializers = arrayInitializer.getInitializers();
                if (initializers == null) {
                    return;
                }
                if (initializers.length != 0) {
                    return;
                }
            } else if (dimensions != null) {

                if (dimensions.length != 1) {
                    return;
                }
                final PsiExpression dimension = dimensions[0];
                final String dimensionText = dimension.getText();
                if (!"0".equals(dimensionText)) {
                    return;
                }
            } else {
                return;
            }
            if (isDeclaredConstant(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isDeclaredConstant(PsiExpression expression) {
            final PsiField field =
                    PsiTreeUtil.getParentOfType(expression, PsiField.class);
            if (field == null) {
                return false;
            }
            return field.hasModifierProperty(PsiModifier.STATIC) &&
                    field.hasModifierProperty(PsiModifier.FINAL);
        }

    }

}
