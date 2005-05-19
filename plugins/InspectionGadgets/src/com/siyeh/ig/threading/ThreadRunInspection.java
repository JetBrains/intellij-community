package com.siyeh.ig.threading;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadRunInspection extends ExpressionInspection {
    private final ThreadRunFix fix = new ThreadRunFix();

    public String getID(){
        return "CallToThreadRun";
    }
    public String getDisplayName() {
        return "Call to 'Thread.run()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Calls to #ref() should probably be replace by start() #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ThreadRunFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with start()";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression) methodNameIdentifier.getParent();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                replaceExpression(methodExpression, "start");
            } else {
                final String qualifierText = qualifier.getText();
                replaceExpression(methodExpression, qualifierText + ".start");
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThreadRunVisitor();
    }

    private static class ThreadRunVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"run".equals(methodName)) {
                return;
            }

            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 0) {
                return;
            }
            final PsiClass methodClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(methodClass, "java.lang.Thread")) {
                return;
            }
            registerMethodCallError(expression);
        }

    }

}
