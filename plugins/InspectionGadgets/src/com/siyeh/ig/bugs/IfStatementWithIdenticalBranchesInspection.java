package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

public class IfStatementWithIdenticalBranchesInspection extends StatementInspection{
    private InspectionGadgetsFix fix = new CollapseIfFix();

    public String getDisplayName(){
        return "'if' statement with identical branches";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
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

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) identifier.getParent();
            final String bodyText = statement.getThenBranch().getText();
            replaceStatement(statement, bodyText);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new IfStatementWithIdenticalBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private static class IfStatementWithIdenticalBranchesVisitor extends BaseInspectionVisitor{
        private IfStatementWithIdenticalBranchesVisitor(BaseInspection inspection,
                                   InspectionManager inspectionManager,
                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

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