package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.siyeh.ig.*;

public class UnnecessaryLabelOnContinueStatementInspection extends StatementInspection {
    private final UnnecessaryLabelOnContinueStatementFix fix = new UnnecessaryLabelOnContinueStatementFix();

    public String getDisplayName() {
        return "Unnecessary label on 'continue' statement";
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

    private static class UnnecessaryLabelOnContinueStatementFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove label";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement continueKeywordElement = descriptor.getPsiElement();
            final PsiContinueStatement continueStatement =
                    (PsiContinueStatement) continueKeywordElement.getParent();
            replaceStatement(project, continueStatement, "continue;");
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryLabelOnContinueStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class UnnecessaryLabelOnContinueStatementVisitor extends BaseInspectionVisitor {
        private PsiStatement currentContainer = null;

        private UnnecessaryLabelOnContinueStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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

        public void visitContinueStatement(PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            if (labelIdentifier == null) {
                return;
            }
            final PsiIdentifier identifier = statement.getLabelIdentifier();
            final String labelText = identifier.getText();
            if (labelText == null || labelText.length() == 0) {
                return;
            }
            final PsiStatement continuedStatement = statement.findContinuedStatement();
            if (continuedStatement == null) {
                return;
            }
            if (currentContainer == null) {
                return;
            }
            final PsiElement containerParent = currentContainer.getParent();
            if (continuedStatement.equals(containerParent)) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
