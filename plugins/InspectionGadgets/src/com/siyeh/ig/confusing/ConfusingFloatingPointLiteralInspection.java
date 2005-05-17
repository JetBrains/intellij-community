package com.siyeh.ig.confusing;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfusingFloatingPointLiteralInspection extends ExpressionInspection {
    private static final Pattern pickyFloatingPointPattern =
            Pattern.compile("[0-9]+\\.[0-9]+((e|E)(-)?[0-9]+)?(f|F|d|D)?");
    private final ConfusingFloatingPointLiteralFix fix = new ConfusingFloatingPointLiteralFix();

    public String getDisplayName() {
        return "Confusing floating-point literal";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Confusing floating point literal #ref #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ConfusingFloatingPointLiteralFix extends InspectionGadgetsFix {
        public String getName() {
            return "Change To canonical form";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiExpression literalExpression = (PsiExpression) descriptor.getPsiElement();
            final String text = literalExpression.getText();
            final String newText = getCanonicalForm(text);
            replaceExpression(literalExpression, newText);
        }

        private static String getCanonicalForm(String text) {
            final String suffix;
            final String prefix;
            if (text.indexOf((int) 'e') > 0) {
                final int breakPoint = text.indexOf((int) 'e');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            } else if (text.indexOf((int) 'E') > 0) {
                final int breakPoint = text.indexOf((int) 'E');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            } else if (text.indexOf((int) 'f') > 0) {
                final int breakPoint = text.indexOf((int) 'f');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            } else if (text.indexOf((int) 'F') > 0) {
                final int breakPoint = text.indexOf((int) 'F');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            } else if (text.indexOf((int) 'd') > 0) {
                final int breakPoint = text.indexOf((int) 'd');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            } else if (text.indexOf((int) 'D') > 0) {
                final int breakPoint = text.indexOf((int) 'D');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            } else {
                suffix = "";
                prefix = text;
            }
            final int indexPoint = prefix.indexOf((int) '.');
            if (indexPoint < 0) {
                return prefix + ".0" + suffix;
            } else if (indexPoint == 0) {
                return '0' + prefix + suffix;
            } else {
                return prefix + '0' + suffix;
            }

        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingFloatingPointLiteralVisitor();
    }

    private static class ConfusingFloatingPointLiteralVisitor extends BaseInspectionVisitor {


        public void visitLiteralExpression(@NotNull PsiLiteralExpression literal) {
            super.visitLiteralExpression(literal);
            final PsiType type = literal.getType();
            if (type == null) {
                return;
            }
            if (!(type.equals(PsiType.FLOAT) || type.equals(PsiType.DOUBLE))) {
                return;
            }
            final String text = literal.getText();
            if (text == null) {
                return;
            }
            if (!isConfusing(text)) {
                return;
            }
            registerError(literal);
        }

        private static boolean isConfusing(String text) {
            final Matcher matcher = pickyFloatingPointPattern.matcher(text);
            return !matcher.matches();
        }
    }

}
