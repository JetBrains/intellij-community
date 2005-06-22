package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

public class IfStatementWithIdenticalBranchesInspection extends StatementInspection{
    private InspectionGadgetsFix fix = new CollapseIfFix();

    public String getDisplayName(){
        return "'if' statement with identical branches";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref statement with identical branches #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class CollapseIfFix extends InspectionGadgetsFix{
        public String getName(){
            return "Collapse 'if' statement";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) identifier.getParent();
            assert statement != null;
            final String bodyText = statement.getThenBranch().getText();
            replaceStatement(statement, bodyText);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IfStatementWithIdenticalBranchesVisitor();
    }

    private static class IfStatementWithIdenticalBranchesVisitor extends BaseInspectionVisitor{

        public void visitIfStatement(@NotNull PsiIfStatement statement){
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiStatement elseBranch = statement.getElseBranch();
            if(EquivalenceChecker.statementsAreEquivalent(thenBranch, elseBranch))
            {
                registerStatementError(statement);
            }
        }

    }
}