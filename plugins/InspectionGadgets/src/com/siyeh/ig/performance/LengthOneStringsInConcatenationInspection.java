package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class LengthOneStringsInConcatenationInspection extends ExpressionInspection {
    private final ReplaceStringsWithCharsFix fix = new ReplaceStringsWithCharsFix();

    public String getID(){
        return "SingleCharacterStringConcatenation";
    }

    public String getDisplayName() {
        return "Single character string concatenation";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String text = location.getText();
        final int length = text.length();
        final String transformedText = '\'' + text.substring(1, length - 1) + '\'';
        return "#ref can be replaced by " + transformedText + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LengthOneStringsInConcatenationVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReplaceStringsWithCharsFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with character";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            final String text = expression.getText();
            final int length = text.length();
            final String character = text.substring(1, length - 1);
            final String charLiteral;
            if ("\'".equals(character)) {
                charLiteral = "'\\''";
            } else {
                charLiteral = '\'' + character + '\'';
            }
            replaceExpression(expression, charLiteral);
        }
    }

    private static class LengthOneStringsInConcatenationVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final String value = (String) expression.getValue();
            if (value == null) {
                return;
            }
            if (value.length() != 1) {
                return;
            }
            if (!isArgumentOfConcatenation(expression) &&
                    !isArgumentOfStringAppend(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isArgumentOfConcatenation(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression) parent;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            if (sign == null) {
                return false;
            }
            if (!sign.getTokenType().equals(JavaTokenType.PLUS)) {
                return false;
            }
            final PsiExpression sibling;
            final PsiExpression lhs = binaryExp.getLOperand();
            if (lhs.equals(expression)) {
                sibling = binaryExp.getROperand();
            } else {
                sibling = lhs;
            }
            if (sibling == null) {
                return false;
            }
            final PsiType siblingType = sibling.getType();
            return TypeUtils.isJavaLangString(siblingType);
        }

        static boolean isArgumentOfStringAppend(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return false;
            }
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            final PsiExpressionList paramList = (PsiExpressionList) parent;
            final PsiExpression[] parameters = paramList.getExpressions();
            if (parameters == null) {
                return false;
            }
            if (parameters.length != 1) {
                return false;
            }
            final PsiElement grandparent = parent.getParent();
            if (!(grandparent instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call = (PsiMethodCallExpression) grandparent;
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if (!"append".equals(name)) {
                return false;
            }
            final PsiMethod method = call.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass == null) {
                return false;
            }
            final String className = methodClass.getQualifiedName();
            return "java.lang.StringBuffer".equals(className) ||
                    "java.lang.StringBuilder".equals(className);
        }
    }

}
