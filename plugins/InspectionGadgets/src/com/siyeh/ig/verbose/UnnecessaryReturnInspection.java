package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class UnnecessaryReturnInspection extends StatementInspection{
    private final UnnecessaryReturnFix fix = new UnnecessaryReturnFix();

    public String getDisplayName(){
        return "Unnecessary 'return' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiMethod method =
                (PsiMethod) PsiTreeUtil.getParentOfType(location,
                                                        PsiMethod.class);
        if(method.isConstructor()){
            return "#ref is unnecessary as the last statement in a constructor #loc";
        } else{
            return "#ref is unnecessary as the last statement in a method returning 'void' #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new UnnecessaryReturnVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryReturnFix extends InspectionGadgetsFix{
        public String getName(){
            return "Remove unnecessary return";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement returnKeywordElement = descriptor.getPsiElement();
            final PsiElement returnStatement = returnKeywordElement.getParent();
            deleteElement(returnStatement);
        }
    }

    private static class UnnecessaryReturnVisitor extends BaseInspectionVisitor{
        private UnnecessaryReturnVisitor(BaseInspection inspection,
                                         InspectionManager inspectionManager,
                                         boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }


        public void visitReturnStatement(PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiMethod method =
                    (PsiMethod) PsiTreeUtil.getParentOfType(statement,
                                                            PsiMethod.class);
            if(method == null)
            {
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
            if(ControlFlowUtils.blockCompletesWithStatement(body, statement))
            {
                registerStatementError(statement);
            }
        }
    }
}
