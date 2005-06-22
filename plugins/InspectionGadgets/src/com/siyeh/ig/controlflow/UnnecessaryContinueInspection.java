package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryContinueInspection extends StatementInspection{
    private final UnnecessaryContinueFix fix = new UnnecessaryContinueFix();

    public String getDisplayName(){
        return "Unnecessary 'continue' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "#ref is unnecessary as the last statement in a loop #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryContinueVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryContinueFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove unnecessary continue";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement returnKeywordElement = descriptor.getPsiElement();
            final PsiElement continueStatement =
                    returnKeywordElement.getParent();
            assert continueStatement!=null;
            deleteElement(continueStatement);
        }
    }

    private static class UnnecessaryContinueVisitor
            extends StatementInspectionVisitor{


        public void visitContinueStatement(@NotNull PsiContinueStatement statement){
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
            if(body == null)
            {
                return;
            }
            if(body instanceof PsiBlockStatement){
                final PsiCodeBlock block =
                        ((PsiBlockStatement) body).getCodeBlock();
                if(ControlFlowUtils.blockCompletesWithStatement(block,
                                                                statement)){
                    registerStatementError(statement);
                }
            } else{
                if(ControlFlowUtils.statementCompletesWithStatement(body,
                                                                statement)){
                    registerStatementError(statement);
                }
            }
        }
    }
}
