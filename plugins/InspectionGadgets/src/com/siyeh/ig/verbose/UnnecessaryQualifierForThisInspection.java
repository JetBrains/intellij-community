package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryQualifierForThisInspection extends ExpressionInspection {
    private final UnnecessaryQualifierForThisFix fix = new UnnecessaryQualifierForThisFix();

    public String getDisplayName() {
        return "Unnecessary qualifier for 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Qualifier '#ref' on 'this' is unnecessary in this context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryQualifierForThisVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryQualifierForThisFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary qualifier ";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement qualifier = descriptor.getPsiElement();
            final PsiThisExpression thisExpression = (PsiThisExpression) qualifier.getParent();
            replaceExpression(thisExpression, "this");
        }

    }

    private static class UnnecessaryQualifierForThisVisitor extends BaseInspectionVisitor {

        public void visitThisExpression(@NotNull PsiThisExpression thisExpression){
            super.visitThisExpression(thisExpression);
            final PsiJavaCodeReferenceElement qualifier =
                    thisExpression.getQualifier();
            if(qualifier == null){
                return;
            }
            final PsiElement referent = qualifier.resolve();
            if(!(referent instanceof PsiClass)){
                return;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(thisExpression);
            if(containingClass == null){
                return;
            }
            if(!containingClass.equals(referent)){
                return;
            }
            registerError(qualifier);
        }
    }

}
