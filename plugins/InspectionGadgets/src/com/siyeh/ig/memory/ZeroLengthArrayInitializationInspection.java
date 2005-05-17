package com.siyeh.ig.memory;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
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
        return GroupNames.MEMORY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Allocation of zero length array #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ZeroLengthArrayInitializationVisitor();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class ZeroLengthArrayInitializationVisitor extends BaseInspectionVisitor {

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
