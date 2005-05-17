package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryLabelOnBreakStatementInspection extends StatementInspection {
    private final UnnecessaryLabelOnBreakStatementFix fix = new UnnecessaryLabelOnBreakStatementFix();

    public String getDisplayName() {
        return "Unnecessary label on 'break' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
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
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiElement breakKeywordElement = descriptor.getPsiElement();
            final PsiBreakStatement breakStatement =
                    (PsiBreakStatement) breakKeywordElement.getParent();
            replaceStatement(breakStatement, "break;");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLabelOnBreakStatementVisitor();
    }

    private static class UnnecessaryLabelOnBreakStatementVisitor extends StatementInspectionVisitor {
        private PsiStatement currentContainer = null;

        public void visitForStatement(@NotNull PsiForStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitForStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitDoWhileStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitForeachStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitWhileStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            final PsiStatement prevContainer = currentContainer;
            currentContainer = statement;
            super.visitSwitchStatement(statement);
            currentContainer = prevContainer;
        }

        public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
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
            if (exitedStatement.equals(currentContainer)) {
              registerStatementError(statement);
            }
        }
    }
}
