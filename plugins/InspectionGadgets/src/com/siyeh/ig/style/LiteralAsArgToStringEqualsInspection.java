package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;

public class LiteralAsArgToStringEqualsInspection extends ExpressionInspection {
    private final SwapEqualsFix swapEqualsFix = new SwapEqualsFix();

    public String getDisplayName() {
        return "expression.equals(\"literal\") rather than \"literal\".equals(expression)";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethodCallExpression expression = (PsiMethodCallExpression) location;
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        return "#ref: String literal is argument of ." + methodName + "(), instead of the target.";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LiteralAsArgToEqualsVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return swapEqualsFix;
    }

    private static class SwapEqualsFix extends InspectionGadgetsFix {
        public String getName() {
            return "Flip .equals()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiMethodCallExpression expression = (PsiMethodCallExpression) descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final PsiExpression target = methodExpression.getQualifierExpression();
            final String methodName = methodExpression.getReferenceName();
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression arg = argumentList.getExpressions()[0];

            final PsiExpression strippedTarget = ParenthesesUtils.stripParentheses(target);
            final PsiExpression strippedArg = ParenthesesUtils.stripParentheses(arg);
            final String callString;
            if (ParenthesesUtils.getPrecendence(strippedArg) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                callString = '(' + strippedArg.getText() + ")." + methodName + '(' + strippedTarget.getText() + ')';
            } else {
                callString = strippedArg.getText() + '.' + methodName + '(' + strippedTarget.getText() + ')';
            }
            replaceExpression(project, expression, callString);
        }
    }

    private static class LiteralAsArgToEqualsVisitor extends BaseInspectionVisitor {
        private LiteralAsArgToEqualsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"equals".equals(methodName) && !"equalsIgnoreCase".equals(methodName)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            if (argList == null) {
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 1) {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiType argType = arg.getType();
            if (argType == null) {
                return;
            }
            if (!(arg instanceof PsiLiteralExpression)) {
                return;
            }
            if (!TypeUtils.isJavaLangString(argType)) {
                return;
            }
            final PsiExpression target = methodExpression.getQualifierExpression();
            if (target instanceof PsiLiteralExpression) {
                return;
            }
            registerError(expression);
        }
    }

}
