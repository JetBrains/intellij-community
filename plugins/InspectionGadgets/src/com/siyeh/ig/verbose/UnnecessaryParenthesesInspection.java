package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ParenthesesUtils;

public class UnnecessaryParenthesesInspection extends StatementInspection {
    private final UnnecessaryParenthesesFix fix = new UnnecessaryParenthesesFix();

    public String getDisplayName() {
        return "Unnecessary parentheses";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Parentheses around #ref are unnecessary #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryParenthesesVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryParenthesesFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary parentheses";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiExpression exp = (PsiExpression) descriptor.getPsiElement();
            final String newExpression = ParenthesesUtils.removeParentheses(exp);
            replaceExpression(project, exp, newExpression);
        }

    }

    private static class UnnecessaryParenthesesVisitor extends BaseInspectionVisitor {
        private UnnecessaryParenthesesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
            final PsiElement parent = expression.getParent();
            final PsiExpression child = expression.getExpression();
            if (child == null) {
                return;
            }
            if (!(parent instanceof PsiExpression)) {
                registerError(expression);
                return;
            }
            final int parentPrecedence = ParenthesesUtils.getPrecendence((PsiExpression) parent);
            final int childPrecedence = ParenthesesUtils.getPrecendence(child);
            if (parentPrecedence > childPrecedence) {
                registerError(expression);
                return;
            }
            if (parentPrecedence == childPrecedence) {
                if (parent instanceof PsiBinaryExpression &&
                        child instanceof PsiBinaryExpression) {
                    final PsiJavaToken parentSign =
                            ((PsiBinaryExpression) parent).getOperationSign();
                    final IElementType parentOperator = parentSign.getTokenType();
                    final PsiJavaToken childSign = ((PsiBinaryExpression) child).getOperationSign();
                    final IElementType childOperator = childSign.getTokenType();

                    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) parent;
                    final PsiExpression lhs = binaryExpression.getLOperand();
                    if (lhs.equals(expression) && parentOperator.equals(childOperator)) {
                        registerError(expression);
                        return;
                    }
                } else {
                    registerError(expression);
                    return;
                }
            }
            super.visitParenthesizedExpression(expression);
        }
    }
}
