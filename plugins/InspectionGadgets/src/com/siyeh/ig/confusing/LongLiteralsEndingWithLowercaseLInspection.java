package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.*;

public class LongLiteralsEndingWithLowercaseLInspection extends ExpressionInspection {
    private final LongLiteralFix fix = new LongLiteralFix();

    public String getDisplayName() {
        return "Long literal ending with 'l' instead of 'L'";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Long literal #ref ends with lowercase 'l' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LongLiteralWithLowercaseLVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class LongLiteralFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace 'l' with 'L'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiExpression literal = (PsiExpression) descriptor.getPsiElement();
            final String text = literal.getText();
            final String newText = text.replace('l', 'L');
            replaceExpression(project, literal, newText);
        }
    }

    private static class LongLiteralWithLowercaseLVisitor extends BaseInspectionVisitor {
        private LongLiteralWithLowercaseLVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLiteralExpression(PsiLiteralExpression expression) {
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
