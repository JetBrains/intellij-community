package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import org.jetbrains.annotations.NotNull;

public class EmptyInitializerInspection extends StatementInspection{
    public String getID(){
        return "EmptyClassInitializer";
    }

    public String getDisplayName(){
        return "Empty class initializer";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Empty class initializer #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new EmptyInitializerFix();
    }

    private static class EmptyInitializerFix extends InspectionGadgetsFix{
        public String getName(){
            return "Delete empty class initializer";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement codeBlock = element.getParent();
            final PsiElement classInitializer = codeBlock.getParent();
            deleteElement(classInitializer);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new EmptyInitializerVisitor();
    }

    private static class EmptyInitializerVisitor extends BaseInspectionVisitor{


        public void visitClassInitializer(@NotNull PsiClassInitializer initializer){
            super.visitClassInitializer(initializer);
            final PsiCodeBlock body = initializer.getBody();
            if(!codeBlockIsEmpty(body)){
                return;
            }
            final PsiJavaToken startingBrace = body.getLBrace();
            registerError(startingBrace);
        }

        private static boolean codeBlockIsEmpty(PsiCodeBlock codeBlock){
            final PsiStatement[] statements = codeBlock.getStatements();
            return statements.length == 0;
        }
    }
}
