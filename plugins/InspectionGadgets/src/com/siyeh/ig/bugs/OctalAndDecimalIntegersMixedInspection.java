package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class OctalAndDecimalIntegersMixedInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Octal and decimal integers in same array";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Octal and decimal integers are in the same array initializer  #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new OctalAndDecimalIntegersMixedVisitor(this, inspectionManager, onTheFly);
    }

    private static class OctalAndDecimalIntegersMixedVisitor extends BaseInspectionVisitor {
        private OctalAndDecimalIntegersMixedVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
            super.visitArrayInitializerExpression(expression);
            final PsiExpression[] initializers = expression.getInitializers();
            boolean hasDecimalLiteral = false;
            boolean hasOctalLiteral = false;
            for (int i = 0; i < initializers.length; i++) {
                final PsiExpression initializer = initializers[i];
                if (initializer instanceof PsiLiteralExpression) {
                    final PsiLiteralExpression literal = (PsiLiteralExpression) initializer;
                    if (isDecimalLiteral(literal)) {
                        hasDecimalLiteral = true;
                    }
                    if (isOctalLiteral(literal)) {
                        hasOctalLiteral = true;
                    }
                }
            }
            if (hasOctalLiteral && hasDecimalLiteral) {
                registerError(expression);
            }
        }

        private static boolean isDecimalLiteral(PsiLiteralExpression literal) {
            final PsiType type = literal.getType();
            if (!PsiType.INT.equals(type) &&
                    !PsiType.LONG.equals(type)) {
                return false;
            }
            final String text = literal.getText();
            if ("0".equals(text)) {
                return false;
            }
            return text.charAt(0) != '0';
        }

        private static boolean isOctalLiteral(PsiLiteralExpression literal) {
            final PsiType type = literal.getType();
            if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
                return false;
            }
            final String text = literal.getText();
            if ("0".equals(text) || "0L".equals(text)) {
                return false;
            }
            return text.charAt(0) == '0' && !text.startsWith("0x") && !text.startsWith("0X");
        }
    }

}
