package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class UnusedLabelInspection extends StatementInspection{
    private final UnusedLabelFix fix = new UnusedLabelFix();

    public String getDisplayName(){
        return "Unused label";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Unused label #ref #loc";
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnusedLabelVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnusedLabelFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove unused label";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement label = descriptor.getPsiElement();
            final PsiLabeledStatement statement =
                    (PsiLabeledStatement) label.getParent();
            final PsiStatement labeledStatement = statement.getStatement();
            final String statementText = labeledStatement.getText();
            replaceStatement(statement, statementText);
        }
    }

    private static class UnusedLabelVisitor extends StatementInspectionVisitor{

        public void visitLabeledStatement(PsiLabeledStatement statement){
            if(containsBreakOrContinueForLabel(statement)){
                return;
            }
            final PsiIdentifier labelIdentifier =
                    statement.getLabelIdentifier();
            registerError(labelIdentifier);
        }

        private static boolean containsBreakOrContinueForLabel(PsiLabeledStatement statement){
            final LabelFinder labelFinder = new LabelFinder(statement);
            statement.accept(labelFinder);
            return labelFinder.jumpFound();
        }
    }

    private static class LabelFinder extends PsiRecursiveElementVisitor{
        private boolean found = false;
        private String label = null;

        private LabelFinder(PsiLabeledStatement target){
            super();
            final PsiIdentifier labelIdentifier = target.getLabelIdentifier();
            label = labelIdentifier.getText();
        }

        public void visitElement(@NotNull PsiElement element){
            if(!found){
                super.visitElement(element);
            }
        }

        public void visitContinueStatement(@NotNull PsiContinueStatement continueStatement){
            if(found){
                return;
            }
            super.visitContinueStatement(continueStatement);

            final PsiIdentifier labelIdentifier =
                    continueStatement.getLabelIdentifier();
            if(labelMatches(labelIdentifier)){
                found = true;
            }
        }

        public void visitBreakStatement(@NotNull PsiBreakStatement breakStatement){
            if(found){
                return;
            }
            super.visitBreakStatement(breakStatement);

            final PsiIdentifier labelIdentifier =
                    breakStatement.getLabelIdentifier();

            if(labelMatches(labelIdentifier)){
                found = true;
            }
        }

        private boolean labelMatches(PsiIdentifier labelIdentifier){
            if(labelIdentifier == null){
                return false;
            }
            final String labelText = labelIdentifier.getText();
            return labelText.equals(label);
        }

        private boolean jumpFound(){
            return found;
        }
    }
}
