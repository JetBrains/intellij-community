package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class UnnecessaryContinueInspection extends StatementInspection {
    private final UnnecessaryContinueFix fix = new UnnecessaryContinueFix();

    public String getDisplayName() {
        return "Unnecessary 'continue' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref is unnecessary as the last statement in a loop #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryContinueVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryContinueFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary continue";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement returnKeywordElement = descriptor.getPsiElement();
            final PsiElement continueStatement = returnKeywordElement.getParent();
            deleteElement(continueStatement);
        }

    }

    private static class UnnecessaryContinueVisitor extends BaseInspectionVisitor {
        private UnnecessaryContinueVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }


        public void visitContinueStatement(PsiContinueStatement statement) {
            final PsiIdentifier identifier = statement.getLabelIdentifier();
            if (identifier != null) {
                return;
            }
            final PsiStatement continuedStatement = statement.findContinuedStatement();
            final PsiElement parent = statement.getParent();
            if (parent.equals(continuedStatement)) {
                registerStatementError(statement);
            } else if (parent instanceof PsiCodeBlock) {
                final PsiCodeBlock block = (PsiCodeBlock) parent;
                if (statementIsLastInBlock(block, statement)) {
                    final PsiElement blockStatement = block.getParent();
                    final PsiElement containingStatement = blockStatement.getParent();
                    if (containingStatement.equals(continuedStatement)) {
                        registerStatementError(statement);
                    }
                }
            }
        }

        private boolean statementIsLastInBlock(PsiCodeBlock block, PsiContinueStatement statement) {
            final PsiStatement[] statements = block.getStatements();
            for (int i = statements.length - 1; i >= 0; i--) {
                final PsiStatement childStatement = statements[i];
                if (statement.equals(childStatement)) {
                    return true;
                }
                if (!(statement instanceof PsiEmptyStatement)) {
                    return false;
                }
            }
            return false;
        }
    }
}
