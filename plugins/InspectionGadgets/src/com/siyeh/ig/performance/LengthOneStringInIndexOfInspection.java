/**
 * (c) 2005 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Jan 16, 2006, 6:26:19 PM
 */
package com.siyeh.ig.performance;

import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class LengthOneStringInIndexOfInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "length.one.string.in.indexof.display.name");
    }

    public String getID() {
        return "SingleCharacterStringConcatenation";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String text = location.getText();
        final int length = text.length();
        final String transformedText = '\'' + text.substring(1, length - 1) +
                '\'';
        return InspectionGadgetsBundle.message(
                "length.one.strings.in.concatenation.problem.descriptor",
                transformedText);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LengthOneStringsInConcatenationVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ReplaceStringsWithCharsFix();
    }

    private static class ReplaceStringsWithCharsFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "length.one.strings.in.concatenation.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression)descriptor.getPsiElement();
            final String text = expression.getText();
            final int length = text.length();
            final String character = text.substring(1, length - 1);
            final String charLiteral;
            if ("\'".equals(character)) {
                charLiteral = "'\\''";
            }
            else {
                charLiteral = '\'' + character + '\'';
            }
            replaceExpression(expression, charLiteral);
        }
    }

    private static class LengthOneStringsInConcatenationVisitor
            extends BaseInspectionVisitor {

        public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final String value = (String)expression.getValue();
            if (value == null) {
                return;
            }
            if (value.length() != 1) {
                return;
            }
            if (!isArgumentOfIndexOf(expression)) {
                return;
            }
            registerError(expression);
        }

        static boolean isArgumentOfIndexOf(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return false;
            }
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            final PsiElement grandparent = parent.getParent();
            if (!(grandparent instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)grandparent;
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            @NonNls final String name = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.INDEX_OF.equals(name) ||
                    "lastIndexOf".equals(name)) {
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
            return "java.lang.String".equals(className);
        }
    }
}