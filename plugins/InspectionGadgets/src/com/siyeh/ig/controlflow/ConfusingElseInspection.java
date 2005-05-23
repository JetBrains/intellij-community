package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfusingElseInspection extends StatementInspection {
    private final ConfusingElseFix fix = new ConfusingElseFix();

    public String getID(){
        return "ConfusingElseBranch";
    }
    public String getDisplayName() {
        return "Confusing 'else' branch";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingElseVisitor();
    }

    @Nullable protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ConfusingElseFix extends InspectionGadgetsFix{
        public String getName(){
            return "Unwrap else branch";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement ifKeyword = descriptor.getPsiElement();
            final PsiIfStatement ifStatement = (PsiIfStatement) ifKeyword.getParent();
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final String text = "if(" + condition.getText() + ')' + thenBranch.getText();
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            if(elseBranch instanceof PsiBlockStatement)
            {
                final PsiBlockStatement elseBlock = (PsiBlockStatement) elseBranch;
                final PsiCodeBlock block = elseBlock.getCodeBlock();
                final PsiElement[] children = block.getChildren();
                if(children.length > 2){
                    final PsiElement containingElement = ifStatement.getParent();
                    final PsiElement added =
                            containingElement.addRangeAfter(children[1],
                                                             children[children
                                                                     .length -
                                                                     2],
                                                             ifStatement);
                    final CodeStyleManager codeStyleManager =
                            CodeStyleManager.getInstance(project);
                    codeStyleManager.reformat(added);
                }
            }
            else
            {
                final PsiElement containingElement = ifStatement.getParent();

                final PsiElement added =
                        containingElement.addAfter(elseBranch,
                                                        ifStatement);
                final CodeStyleManager codeStyleManager =
                        CodeStyleManager.getInstance(project);
                codeStyleManager.reformat(added);
            }
            replaceStatement(ifStatement, text);
        }
    }

    public String buildErrorString(PsiElement location) {
        return "#ref branch may be unwrapped, as the if branch never completes #loc";
    }

    private static class ConfusingElseVisitor extends StatementInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (elseBranch instanceof PsiIfStatement) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
                return;
            }

            final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);

            if (nextStatement == null) {
                return;
            }
            if (!ControlFlowUtils.statementMayCompleteNormally(elseBranch)) {
                return;         //protecting against an edge case where both branches return
                // and are followed by a case label
            }

            final PsiElement elseToken = statement.getElseElement();
            registerError(elseToken);
        }
    }
}
