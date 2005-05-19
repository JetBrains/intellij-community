package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class StringEqualityInspection extends ExpressionInspection {
    private final EqualityToEqualsFix fix = new EqualityToEqualsFix();

    public String getDisplayName() {
        return "String comparison using '==', instead of '.equals()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "String values are compared using '#ref', not '.equals()' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualityVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class EqualityToEqualsFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with .equals()";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
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
            replaceExpression(expression, newExpression);
        }
    }

    private static class ObjectEqualityVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
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
            return TypeUtils.isJavaLangString(lhsType);
        }
    }

}
