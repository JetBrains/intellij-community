package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.*;

public class StringEqualityInspection extends ExpressionInspection {
    private final EqualityToEqualsFix fix = new EqualityToEqualsFix();

    public String getDisplayName() {
        return "String comparison using ==, instead of '.equals()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "String values are compared using '#ref', not '.equals()' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ObjectEqualityVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class EqualityToEqualsFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with .equals()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement comparisonToken = descriptor.getPsiElement();
            boolean negated = false;
            final PsiBinaryExpression expression =
                    (PsiBinaryExpression) comparisonToken.getParent();
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.NE)) {
                negated = true;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
            final PsiExpression rhs = expression.getROperand();

            final PsiExpression strippedRhs = ParenthesesUtils.stripParentheses(rhs);

            final String expString;
            if (ParenthesesUtils.getPrecendence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = '(' + strippedLhs.getText() + ").equals(" + strippedRhs.getText() + ')';
            } else {
                expString = strippedLhs.getText() + ".equals(" + strippedRhs.getText() + ')';
            }
            final String newExpression;
            if (negated) {
                newExpression = '!' + expString;
            } else {
                newExpression = expString;
            }
            replaceExpression(project, expression, newExpression);
        }
    }

    private static class ObjectEqualityVisitor extends BaseInspectionVisitor {
        private ObjectEqualityVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!isStringType(lhs)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (!isStringType(rhs)) {
                return;
            }
            final String lhsText = lhs.getText();
            if ("null".equals(lhsText)) {
                return;
            }
            final String rhsText = rhs.getText();
            if ("null".equals(rhsText)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            registerError(sign);
        }

        private static boolean isStringType(PsiExpression lhs) {
            if (lhs == null) {
                return false;
            }
            final PsiType lhsType = lhs.getType();
            if (lhsType == null) {
                return false;
            }
            return !ClassUtils.isPrimitive(lhsType)
                    && !lhsType.equals(PsiType.NULL)
                    && TypeUtils.isJavaLangString(lhsType);
        }
    }

}
