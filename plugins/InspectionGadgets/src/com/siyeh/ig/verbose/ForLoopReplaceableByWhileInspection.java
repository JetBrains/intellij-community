package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ForLoopReplaceableByWhileInspection extends StatementInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreLoopsWithoutConditions = false;
    private final ReplaceForByWhileFix fix = new ReplaceForByWhileFix();

    public String getID(){
        return "ForLoopReplaceableByWhile";
    }
    public String getDisplayName() {
        return "'for' loop may be replaced by 'while' loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' loop statement may be replace by 'while' loop #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore 'infinite' for loops without conditions",
                                              this, "m_ignoreLoopsWithoutConditions");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReplaceForByWhileFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with 'while'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
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
            replaceStatement(forStatement, whileStatement);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ForLoopReplaceableByWhileVisitor(this, inspectionManager, onTheFly);
    }

    private  class ForLoopReplaceableByWhileVisitor extends StatementInspectionVisitor {
        private ForLoopReplaceableByWhileVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement initialization = statement.getInitialization();
            if (initialization != null && !(initialization instanceof PsiEmptyStatement)) {
                return;
            }
            final PsiStatement update = statement.getUpdate();
            if (update != null && !(update instanceof PsiEmptyStatement)) {
                return;
            }
            if(m_ignoreLoopsWithoutConditions)
            {
                final PsiExpression condition = statement.getCondition();
                if(condition == null){
                    return;
                }
                final String conditionText = condition.getText();
                if("true".equals(conditionText)){
                    return;
                }
            }
            registerStatementError(statement);
        }
    }
}
