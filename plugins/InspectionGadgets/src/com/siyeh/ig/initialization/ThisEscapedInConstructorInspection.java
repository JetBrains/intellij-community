package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class ThisEscapedInConstructorInspection extends ClassInspection {

    public String getDisplayName() {
        return "'this' reference escaped in constructor";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Escape of '#ref' during object construction #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ThisExposedInConstructorInspectionVisitor(this,
                inspectionManager,
                onTheFly);
    }

    private static class ThisExposedInConstructorInspectionVisitor
            extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        private ThisExposedInConstructorInspectionVisitor(BaseInspection inspection, InspectionManager inspectionManager,
                                                          boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            final boolean wasInClass = m_inClass;
            if (!m_inClass) {

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitNewExpression(PsiNewExpression psiNewExpression) {

            super.visitNewExpression(psiNewExpression);

            final PsiMember methodOrInitializer = checkConstructorOrInstanceInitializer(psiNewExpression);
            if (methodOrInitializer == null) {
                return;
            }

            if (psiNewExpression.getClassReference() == null) {
                return;
            }

            final PsiThisExpression thisExposed = checkArgumentsForThis(psiNewExpression);
            if (thisExposed == null) {
                return;
            }

            final PsiJavaCodeReferenceElement refElement = psiNewExpression.getClassReference();
            if (refElement != null) {
                final PsiClass constructorClass = (PsiClass) refElement.resolve();

                if (constructorClass != null) {
                    // Skips inner classes and containing classes (as well as top level package class with file-named class)
                    if (constructorClass.getContainingFile().equals(psiNewExpression.getContainingFile())) {
                        return;
                    }
                }
            }

            registerError(thisExposed);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression assignment) {

            super.visitAssignmentExpression(assignment);

            final PsiMember methodOrInitializer = checkConstructorOrInstanceInitializer(assignment);
            if (methodOrInitializer == null) {
                return;
            }

            final PsiExpression psiExpression = getLastRightExpression(assignment);

            if (psiExpression == null ||
                    !(psiExpression instanceof PsiThisExpression)) {
                return;
            }
            final PsiThisExpression thisExpression = (PsiThisExpression) psiExpression;

            // Need to confirm that LeftExpression is outside of class relatives
            if (!(assignment.getLExpression() instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression leftExpression = (PsiReferenceExpression) assignment.getLExpression();
            if (!(leftExpression.resolve() instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) leftExpression.resolve();

            if (field.getContainingFile().equals(assignment.getContainingFile())) {
                return;
            }

            // Inheritance check
            final PsiClass cls = ClassUtils.getContainingClass(assignment);
            if (cls.isInheritor(field.getContainingClass(), true)) {
                return;
            }

            registerError(thisExpression);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);

            final PsiMember methodOrInitializer = checkConstructorOrInstanceInitializer(call);
            if (methodOrInitializer == null) {
                return;
            }

            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final PsiMethod calledMethod = call.resolveMethod();
            if (calledMethod == null) {
                return;
            }
            if (calledMethod.isConstructor()) {
                return;
            }

            final PsiClass calledMethodClass = calledMethod.getContainingClass();
            final PsiClass methodClass = methodOrInitializer.getContainingClass();

            if (calledMethodClass.equals(methodClass))   // compares class types statically?
            {
                return;
            }
            final PsiThisExpression thisExposed = checkArgumentsForThis(call);
            if (thisExposed == null) {
                return;
            }

            // Methods - static or not - from superclasses don't trigger
            if (methodClass.isInheritor(calledMethodClass, true)) {
                return;
            }

            // Make sure using this with members of self or superclasses doesn't trigger
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression qualifiedExpression =
                    (PsiReferenceExpression) qualifier;
            final PsiElement referent = qualifiedExpression.resolve();
            if (referent instanceof PsiField) {
                final PsiField field = (PsiField) referent;
                final PsiClass containingClass = field.getContainingClass();

                if (methodClass.equals(containingClass) ||
                        methodClass.isInheritor(containingClass, true)) {
                    return;
                }
            }

            registerError(thisExposed);
        }

        // Get rightmost expression of assignment. Used when assignments are chained. Recursive
        private static PsiExpression getLastRightExpression(PsiAssignmentExpression assignmentExp) {

            if (assignmentExp == null) {
                return null;
            }

            final PsiExpression expression = assignmentExp.getRExpression();
            if (expression == null) {
                return null;
            }

            if (expression instanceof PsiAssignmentExpression) {
                return getLastRightExpression((PsiAssignmentExpression) expression);
            }
            return expression;
        }

        /**
         * @param call
         * @return null unless CallExpression is a Constructor or an Instance
         *         Initializer. Otherwise it returns the PsiMember representing
         *         the contructor/initializer
         */
        private static PsiMember checkConstructorOrInstanceInitializer(PsiElement call) {
            final PsiMethod method = (PsiMethod) PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            PsiMember methodOrInitializer = method;
            if (method == null) {
                final PsiClassInitializer classInitializer = (PsiClassInitializer) PsiTreeUtil.getParentOfType(call, PsiClassInitializer.class);
                if (classInitializer == null) {
                    return null;
                }
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                    return null;
                }
                methodOrInitializer = classInitializer;

            } else if (!method.isConstructor()) {
                return null;
            }
            return methodOrInitializer;
        }

        // If there are more than two of 'this' as arguments, only marks the first until it is removed. No big deal.
        private static PsiThisExpression checkArgumentsForThis(PsiCall call) {
            final PsiExpressionList peList = call.getArgumentList();
            if (peList == null) {   // array initializer
                return null;
            }
            final PsiExpression[] argExpressions = peList.getExpressions();
            for (int i = 0; i < argExpressions.length; i++) {
                final PsiExpression argExpression = argExpressions[i];

                if (argExpression instanceof PsiThisExpression) {
                    return (PsiThisExpression) argExpression;
                }
            }
            return null;
        }
    }

}
