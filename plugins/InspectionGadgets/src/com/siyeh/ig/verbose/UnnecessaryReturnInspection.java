package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;

public class UnnecessaryReturnInspection extends StatementInspection {
    private final UnnecessaryReturnFix fix = new UnnecessaryReturnFix();

    public String getDisplayName() {
        return "Unnecessary 'return' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method =
                (PsiMethod) PsiTreeUtil.getParentOfType(location, PsiMethod.class);
        if (method.isConstructor()) {
            return "#ref is unnecessary as the last statement in a constructor #loc";
        } else {
            return "#ref is unnecessary as the last statement in a method returning 'void' #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryReturnVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryReturnFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary return";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement returnKeywordElement = descriptor.getPsiElement();
            final PsiElement returnStatement = returnKeywordElement.getParent();
            deleteElement(returnStatement);
        }

    }

    private static class UnnecessaryReturnVisitor extends BaseInspectionVisitor {
        private UnnecessaryReturnVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // don't call super, to keep from drilling in
            final PsiType returnType = method.getReturnType();
            if (!method.isConstructor() && !returnType.equals(PsiType.VOID)) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null || statements.length == 0) {
                return;
            }
            final PsiStatement finalStatement = statements[statements.length - 1];
            if (!(finalStatement instanceof PsiReturnStatement)) {
                return;
            }
            registerStatementError(finalStatement);
        }
    }
}
