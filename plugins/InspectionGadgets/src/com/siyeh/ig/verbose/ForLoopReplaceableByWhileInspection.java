package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class ForLoopReplaceableByWhileInspection extends StatementInspection {
    private final ReplaceForByWhileFix fix = new ReplaceForByWhileFix();

    public String getDisplayName() {
        return "'for' loop may be replaced by 'while' loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' loop statement may be replace by 'while' loop #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReplaceForByWhileFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with 'while'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement forKeywordElement = descriptor.getPsiElement();
            final PsiForStatement forStatement =
                    (PsiForStatement) forKeywordElement.getParent();
            final PsiExpression condition = forStatement.getCondition();
            final PsiStatement body = forStatement.getBody();
            final String whileStatement;
            if (condition == null) {
                whileStatement = "while(true)" + body.getText();
            } else {
                whileStatement = "while(" + condition.getText() + ')' + body.getText();
            }
            replaceStatement(project, forStatement, whileStatement);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ForLoopReplaceableByWhileVisitor(this, inspectionManager, onTheFly);
    }

    private static class ForLoopReplaceableByWhileVisitor extends BaseInspectionVisitor {
        private ForLoopReplaceableByWhileVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement initialization = statement.getInitialization();
            if (initialization != null && !(initialization instanceof PsiEmptyStatement)) {
                return;
            }
            final PsiStatement update = statement.getUpdate();
            if (update != null && !(update instanceof PsiEmptyStatement)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}
