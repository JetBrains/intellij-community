package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.*;

public class UnnecessaryLabelOnBreakStatementInspection extends StatementInspection {
    private final UnnecessaryLabelOnBreakStatementFix fix = new UnnecessaryLabelOnBreakStatementFix();

    public String getDisplayName() {
        return "Unnecessary label on 'break' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unnecessary label on #ref statement #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryLabelOnBreakStatementFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove label";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement breakKeywordElement = descriptor.getPsiElement();
            final PsiBreakStatement breakStatement =
                    (PsiBreakStatement) breakKeywordElement.getParent();
            replaceStatement(project, breakStatement, "break;");
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryLabelOnBreakStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class UnnecessaryLabelOnBreakStatementVisitor extends BaseInspectionVisitor {
        private PsiStatement currentContainer = null;

        private UnnecessaryLabelOnBreakStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(PsiForStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitForStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitDoWhileStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitForeachStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitWhileStatement(PsiWhileStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitWhileStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitSwitchStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitBreakStatement(PsiBreakStatement statement) {
            super.visitBreakStatement(statement);
            final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            if (labelIdentifier == null) {
                return;
            }
            final PsiIdentifier identifier = statement.getLabelIdentifier();
            final String labelText = identifier.getText();
            if (labelText == null || labelText.length() == 0) {
                return;
            }
            final PsiStatement exitedStatement = statement.findExitedStatement();
            if (exitedStatement == null) {
                return;
            }
            if (currentContainer == null) {
                return;
            }
            final PsiElement containerParent = currentContainer.getParent();
            if (exitedStatement.equals(containerParent)) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
