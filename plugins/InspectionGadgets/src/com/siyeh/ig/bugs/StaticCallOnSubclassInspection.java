package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class StaticCallOnSubclassInspection extends ExpressionInspection {
    public String getID(){
        return "StaticMethodReferencedViaSubclass";
    }
    private final StaticCallOnSubclassFix fix = new StaticCallOnSubclassFix();


    public String getDisplayName() {
        return "Static method referenced via subclass";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiReferenceExpression methodExpression = (PsiReferenceExpression) location.getParent();
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) methodExpression.getParent();
        final PsiMethod method = methodCall.resolveMethod();
        final PsiClass containingClass = method.getContainingClass();
        final String declaringClass = containingClass.getName();
        final PsiElement qualifier = methodExpression.getQualifier();
        final String referencedClass = qualifier.getText();
        return "Static method '#ref' declared on class " + declaringClass + " but referenced via class" + referencedClass + "    #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class StaticCallOnSubclassFix extends InspectionGadgetsFix {
        public String getName() {
            return "Rationalize static method call";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiIdentifier name = (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression = (PsiReferenceExpression) name.getParent();
            final PsiMethodCallExpression call = (PsiMethodCallExpression) expression.getParent();
            final PsiMethod method = call.resolveMethod();
            final String methodName = expression.getReferenceName();
            final PsiClass containingClass = method.getContainingClass();
            final PsiExpressionList argumentList = call.getArgumentList();
            final String containingClassName = containingClass.getName();
            final String argText = argumentList.getText();
            replaceExpression(project, call, containingClassName + '.' + methodName + argText );
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StaticCallOnSubclassVisitor(this, inspectionManager, onTheFly);
    }

    private static class StaticCallOnSubclassVisitor extends BaseInspectionVisitor {
        private StaticCallOnSubclassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final PsiElement qualifier = methodExpression.getQualifier();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            if(!method.hasModifierProperty(PsiModifier.STATIC))
            {
                return;
            }

            final PsiElement referent = ((PsiReference) qualifier).resolve();
            if (!(referent instanceof PsiClass)) {
                return;
            }
            final PsiClass referencedClass = (PsiClass) referent;


            final PsiClass declaringClass = method.getContainingClass();
            if (declaringClass.equals(referencedClass)) {
                return;
            }

            final PsiClass containingClass = ClassUtils.getContainingClass(call);
            if(!ClassUtils.isClassVisibleFromClass(containingClass, declaringClass))
            {
                return;
            }
            registerMethodCallError(call);
        }


    }

}
