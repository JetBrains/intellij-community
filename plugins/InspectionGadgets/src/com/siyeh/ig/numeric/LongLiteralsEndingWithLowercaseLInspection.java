package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class LongLiteralsEndingWithLowercaseLInspection extends ExpressionInspection {
    private final LongLiteralFix fix = new LongLiteralFix();

    public String getID(){
        return "LongLiteralEndingWithLowercaseL";
    }

    public String getDisplayName() {
        return "Long literal ending with 'l' instead of 'L'";
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Long literal #ref ends with lowercase 'l' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LongLiteralWithLowercaseLVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class LongLiteralFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace 'l' with 'L'";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiExpression literal = (PsiExpression) descriptor.getPsiElement();
            final String text = literal.getText();
            final String newText = text.replace('l', 'L');
            replaceExpression(literal, newText);
        }
    }

    private static class LongLiteralWithLowercaseLVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!type.equals(PsiType.LONG)) {
                return;
            }
            final String text = expression.getText();
            if (text == null) {
                return;
            }
            final int length = text.length();
            if (length == 0) {
                return;
            }
            if (text.charAt(length - 1) != 'l') {
                return;
            }
            registerError(expression);
        }
    }

}
