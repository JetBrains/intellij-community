package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class UnnecessarySuperConstructorInspection extends ExpressionInspection {
    private final UnnecessarySuperConstructorFix fix = new UnnecessarySuperConstructorFix();

    public String getID(){
        return "UnnecessaryCallToSuper";
    }

    public String getDisplayName() {
        return "Unnecessary call to 'super()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref is unnecessary #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarySuperConstructorVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessarySuperConstructorFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary super()";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement superCall = descriptor.getPsiElement();
            final PsiElement callStatement = superCall.getParent();
            assert callStatement !=null;
            deleteElement(callStatement);
        }

    }

    private static class UnnecessarySuperConstructorVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodText = methodExpression.getText();
            if (!"super".equals(methodText)) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 0) {
                return;
            }
            registerError(call);
        }
    }
}
