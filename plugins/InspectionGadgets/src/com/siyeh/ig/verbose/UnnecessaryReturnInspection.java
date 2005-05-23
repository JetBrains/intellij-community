package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryReturnInspection extends StatementInspection{
    private final UnnecessaryReturnFix fix = new UnnecessaryReturnFix();

    public String getID(){
        return "UnnecessaryReturnStatement";
    }

    public String getDisplayName(){
        return "Unnecessary 'return' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(location,
                                                        PsiMethod.class);
        assert method != null;
        if(method.isConstructor()){
            return "#ref is unnecessary as the last statement in a constructor #loc";
        } else{
            return "#ref is unnecessary as the last statement in a method returning 'void' #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryReturnVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryReturnFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove unnecessary return";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement returnKeywordElement = descriptor.getPsiElement();
            final PsiElement returnStatement = returnKeywordElement.getParent();
            deleteElement(returnStatement);
        }
    }

    private static class UnnecessaryReturnVisitor extends StatementInspectionVisitor{

        public void visitReturnStatement(@NotNull PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            if(method == null){
            return;
            }
            if(!method.isConstructor()){
                final PsiType returnType = method.getReturnType();
                if(!PsiType.VOID.equals(returnType)){
                    return;
                }
            }
            final PsiCodeBlock body = method.getBody();
            if(body == null){
                return;
            }
            if(ControlFlowUtils.blockCompletesWithStatement(body, statement)){
                registerStatementError(statement);
            }
        }
    }
}
