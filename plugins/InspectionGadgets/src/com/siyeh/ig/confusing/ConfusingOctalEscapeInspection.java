package com.siyeh.ig.confusing;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ConfusingOctalEscapeInspection extends ExpressionInspection {
    public String getID(){
        return "ConfusingOctalEscapeSequence";
    }
    public String getDisplayName() {
        return "Confusing octal escape sequence";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "String #ref contains potentially confusing octal escape sequence #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingOctalEscapeVisitor();
    }

    private static class ConfusingOctalEscapeVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(@NotNull PsiLiteralExpression exp) {
            super.visitLiteralExpression(exp);
            if (!TypeUtils.expressionHasType("java.lang.String", exp)) {
                return;
            }
            final String text = exp.getText();
            if (!containsConfusingOctalEscape(text)) {
                return;
            }
            registerError(exp);
        }

    }

    private static boolean containsConfusingOctalEscape(String text) {
        int escapeStart = -1;
        while (true) {
            escapeStart = text.indexOf((int) '\\', escapeStart + 1);
            if (escapeStart < 0) {
                return false;
            }
            int digitPosition = escapeStart + 1;
            while (digitPosition < text.length() &&
                    Character.isDigit(text.charAt(digitPosition))) {
                digitPosition++;
            }
            if (digitPosition > escapeStart + 1) {
                final String escapeString = text.substring(escapeStart + 1, digitPosition);
                if (escapeString.length() > 3) {
                    return true;
                }
                if (escapeString.indexOf((int) '8') > 0 ||
                        escapeString.indexOf((int) '9') > 0) {
                    return true;
                }
            }
        }
    }


}
