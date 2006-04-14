package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class UnaryPlusInspection extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnaryPlusVisitor();
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unary.plus.problem.descriptor");
    }
    private static class UnaryPlusVisitor extends BaseInspectionVisitor {

        public void visitPrefixExpression(PsiPrefixExpression prefixExpression) {
            super.visitPrefixExpression(prefixExpression);
            final PsiJavaToken token = prefixExpression.getOperationSign();
            final IElementType tokenType = token.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUS)) {
                return;
            }
            registerError(token);
        }
    }
}
