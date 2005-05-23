package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryLabelOnContinueStatementInspection extends StatementInspection {
    private final UnnecessaryLabelOnContinueStatementFix fix = new UnnecessaryLabelOnContinueStatementFix();

    public String getDisplayName() {
        return "Unnecessary label on 'continue' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
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

    private static class UnnecessaryLabelOnContinueStatementFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove label";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement continueKeywordElement = descriptor.getPsiElement();
            final PsiContinueStatement continueStatement =
                    (PsiContinueStatement) continueKeywordElement.getParent();
            replaceStatement(continueStatement, "continue;");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLabelOnContinueStatementVisitor();
    }

    private static class UnnecessaryLabelOnContinueStatementVisitor extends StatementInspectionVisitor {
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

        public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
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
            if (continuedStatement.equals(currentContainer)) {
              registerStatementError(statement);
            }
        }
    }
}
