package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiThisExpression;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class UnnecessaryQualifierForThisInspection extends StatementInspection {
    private final UnnecessaryThisFix fix = new UnnecessaryThisFix();

    public String getDisplayName() {
        return "Unnecessary qualifier for 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Qualifier '#ref' on 'this' is unnecessary in this context #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryQualifierForThisVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryThisFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary qualifier ";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement qualifier = descriptor.getPsiElement();
            final PsiThisExpression thisExpression = (PsiThisExpression) qualifier.getParent();
            replaceExpression(project, thisExpression, "this");
        }

    }

    private static class UnnecessaryQualifierForThisVisitor extends BaseInspectionVisitor {
        private UnnecessaryQualifierForThisVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitThisExpression(PsiThisExpression thisExpression) {
            super.visitThisExpression(thisExpression);
            final PsiJavaCodeReferenceElement qualifier =
                    thisExpression.getQualifier();
            if (qualifier == null) {
                return;
            }
            final PsiElement referent = qualifier.resolve();
            if (referent == null) {
                return;
            }
            if (!(referent instanceof PsiClass)) {
                return;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(thisExpression);
            if (containingClass == null) {
                return;
            }
            if (!containingClass.equals(referent)) {
                return;
            }
            registerError(qualifier);
        }
    }

}
