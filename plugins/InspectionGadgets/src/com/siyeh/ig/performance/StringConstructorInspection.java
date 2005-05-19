package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class StringConstructorInspection extends ExpressionInspection {
    public String getID(){
        return "RedundantStringConstructorCall";
    }

    public String getDisplayName() {
        return "Redundant String constructor call";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "#ref is redundant #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringConstructorVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new StringConstructorFix((PsiNewExpression) location);
    }

    private static class StringConstructorFix extends InspectionGadgetsFix {
        private final String m_name;

        private StringConstructorFix(PsiNewExpression expression) {
            super();
            final PsiExpressionList argList = expression.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            if (args.length == 1) {
                m_name = "Replace with arg";
            } else {
                m_name = "Replace with empty string";
            }
        }

        public String getName() {
            return m_name;
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiNewExpression expression = (PsiNewExpression) descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final String argText;
            if (args.length == 1) {
                argText = args[0].getText();
            } else {
                argText = "\"\"";
            }
            replaceExpression(expression, argText);
        }
    }

    private static class StringConstructorVisitor extends BaseInspectionVisitor {


        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            if (argList == null) {
                return;
            }
            final PsiExpression[] args = argList.getExpressions();

            if (args.length > 1) {
                return;
            }
            if (args.length == 1) {
                final PsiType parameterType = args[0].getType();
                if (!TypeUtils.isJavaLangString(parameterType)) {
                    return;
                }
            }
            registerError(expression);
        }
    }

}
