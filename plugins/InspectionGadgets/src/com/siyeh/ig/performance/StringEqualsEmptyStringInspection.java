package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.TypeUtils;

public class StringEqualsEmptyStringInspection extends ExpressionInspection {
    private final StringEqualsEmptyStringFix fix = new StringEqualsEmptyStringFix();

    public String getDisplayName() {
        return "'String.equals(\"\")'";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return ".equals(\"\") can be replace by .length()==0 #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class StringEqualsEmptyStringFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with .length()==0";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiIdentifier name = (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression = (PsiReferenceExpression) name.getParent();
            final PsiExpression call = (PsiExpression) expression.getParent();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = qualifier.getText();
            final PsiElement parent = call.getParent();
            if(parent instanceof PsiExpression && BoolUtils.isNegation( (PsiExpression) parent))
            {
                replaceExpression(project, (PsiExpression) parent, qualifierText + ".length()!=0");
            }
            else
            {
                replaceExpression(project, call, qualifierText + ".length()==0");
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringEqualsEmptyStringVisitor(this, inspectionManager, onTheFly);
    }

    private static class StringEqualsEmptyStringVisitor extends BaseInspectionVisitor {
        private StringEqualsEmptyStringVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"equals".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            if (!isEmptyStringLiteral(args[0])) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiElement context = call.getParent();
            if (context instanceof PsiExpressionStatement) {
                return; //cheesy, but necessary, because otherwise the quickfix will produce
                //uncompilable code (out of merely incorrect code).
            }
            registerMethodCallError(call);
        }

        private static boolean isEmptyStringLiteral(PsiExpression arg) {
            final PsiType type = arg.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return false;
            }
            if (!(arg instanceof PsiLiteralExpression)) {
                return false;
            }
            final PsiLiteralExpression literal = (PsiLiteralExpression) arg;
            final String value = (String) literal.getValue();
            if (value == null) {
                return false;
            }
            if (value.length() != 0) {
                return false;
            }
            return true;
        }

    }

}
