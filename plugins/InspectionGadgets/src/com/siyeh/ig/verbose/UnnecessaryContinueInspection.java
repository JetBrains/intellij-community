package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class UnnecessaryContinueInspection extends StatementInspection{
    private final UnnecessaryContinueFix fix = new UnnecessaryContinueFix();

    public String getDisplayName(){
        return "Unnecessary 'continue' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref is unnecessary as the last statement in a loop #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new UnnecessaryContinueVisitor(this, inspectionManager,
                                              onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryContinueFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove unnecessary continue";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            final PsiElement returnKeywordElement = descriptor.getPsiElement();
            final PsiElement continueStatement =
                    returnKeywordElement.getParent();
            deleteElement(continueStatement);
        }
    }

    private static class UnnecessaryContinueVisitor
            extends BaseInspectionVisitor{
        private UnnecessaryContinueVisitor(BaseInspection inspection,
                                           InspectionManager inspectionManager,
                                           boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitContinueStatement(PsiContinueStatement statement){
            final PsiStatement continuedStatement =
                    statement.findContinuedStatement();
            PsiStatement body = null;
            if(continuedStatement instanceof PsiForeachStatement){
                body = ((PsiForeachStatement) continuedStatement).getBody();
            } else if(continuedStatement instanceof PsiForStatement){
                body = ((PsiForStatement) continuedStatement).getBody();
            } else if(continuedStatement instanceof PsiDoWhileStatement){
                body = ((PsiDoWhileStatement) continuedStatement).getBody();
            } else if(continuedStatement instanceof PsiWhileStatement){
                body = ((PsiWhileStatement) continuedStatement).getBody();
            }

            if(body == null){
                return;
            }
            if(!(body instanceof PsiBlockStatement)){
                registerStatementError(statement);
                return;
            }
            final PsiCodeBlock block = ((PsiBlockStatement) body).getCodeBlock();
            if(ControlFlowUtils.blockCompletesWithStatement(block, statement)){
                registerStatementError(statement);
            }
        }

    }
}
