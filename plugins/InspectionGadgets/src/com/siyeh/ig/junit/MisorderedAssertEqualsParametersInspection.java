package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class MisorderedAssertEqualsParametersInspection extends ExpressionInspection {
    private final FlipParametersFix fix = new FlipParametersFix();


    public String getDisplayName() {
        return "Misordered 'assertEquals()' parameters";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Parameters to #ref() in wrong order #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class FlipParametersFix extends InspectionGadgetsFix {
        public String getName() {
            return "Flip compared parameters";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiMethodCallExpression callExpression = (PsiMethodCallExpression) methodNameIdentifier.getParent().getParent();
            final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();

            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            final PsiManager psiManager = callExpression.getManager();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType = PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final int expectedPosition;
            final int actualPosition;
            if (paramType1.equals(stringType) && parameters.length > 2) {
                expectedPosition = 1;
                actualPosition = 2;
            } else {
                expectedPosition = 0;
                actualPosition = 1;
            }
            final PsiExpressionList argumentList = callExpression.getArgumentList();

            final PsiExpression[] args = argumentList.getExpressions();
            final PsiExpression expectedArg = args[expectedPosition];
            final PsiExpression actualArg = args[actualPosition];
            final String actualArgText = actualArg.getText();
            final String expectedArgText = expectedArg.getText();
            replaceExpression(project, expectedArg, actualArgText);
            replaceExpression(project, actualArg, expectedArgText);
        }

    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MisorderedAssertEqualsParametersVisitor(this, inspectionManager, onTheFly);
    }

    private static class MisorderedAssertEqualsParametersVisitor extends BaseInspectionVisitor {

        private MisorderedAssertEqualsParametersVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isAssertEquals(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();

            final PsiManager psiManager = expression.getManager();

            final Project project = psiManager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType = PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final int expectedPosition;
            final int actualPosition ;
            if (paramType1.equals(stringType)&& parameters.length > 2) {
                expectedPosition = 1;
                actualPosition = 2;
            } else {
                expectedPosition = 0;
                actualPosition = 1;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null)
            {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            final PsiExpression expectedArg = args[expectedPosition];
            final PsiExpression actualArg = args[actualPosition];
            if(expectedArg == null || actualArg == null)
            {
                return;
            }
            if(expectedArg instanceof PsiLiteralExpression)
            {
                return;
            }
            if (!(actualArg instanceof PsiLiteralExpression))
            {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isAssertEquals(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null)
            {
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"assertEquals".equals(methodName)) {
                return false;
            }
            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            if (method == null) {
                return false;
            }

            final PsiClass targetClass = method.getContainingClass();
            return targetClass != null &&
                           ClassUtils.isSubclass(targetClass,
                                                 "junit.framework.Assert");
        }

    }

}
