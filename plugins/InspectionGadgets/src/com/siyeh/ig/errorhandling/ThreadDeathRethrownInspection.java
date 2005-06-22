package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadDeathRethrownInspection extends StatementInspection{
    public String getID(){
        return "ThreadDeathNotRethrown";
    }

    public String getDisplayName(){
        return "java.lang.ThreadDeath not rethrown";
    }

    public String getGroupDisplayName(){
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref not rethrown #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ThreadDeathRethrownVisitor();
    }

    private static class ThreadDeathRethrownVisitor
            extends StatementInspectionVisitor{


        public void visitTryStatement(@NotNull PsiTryStatement statement){
            super.visitTryStatement(statement);
            final PsiCatchSection[] catchSections = statement.getCatchSections();
            for(PsiCatchSection catchSection : catchSections){
                final PsiParameter parameter = catchSection.getParameter();
                final PsiCodeBlock catchBlock =
                        catchSection.getCatchBlock();
                if(parameter != null && catchBlock != null){
                    checkCatchBlock(parameter, catchBlock);
                }
            }
        }

        private void checkCatchBlock(PsiParameter parameter,
                                     PsiCodeBlock catchBlock){
            final PsiType type = parameter.getType();
            if(!TypeUtils.typeEquals("java.lang.ThreadDeath", type)){
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            if(typeElement == null){
                return;
            }
            final PsiStatement[] statements = catchBlock.getStatements();
            if(statements.length == 0){
                registerError(typeElement);
                return;
            }
            final PsiStatement lastStatement =
                    statements[statements.length - 1];
            if(!(lastStatement instanceof PsiThrowStatement)){
                registerError(typeElement);
                return;
            }
            final PsiThrowStatement throwStatement =
                    (PsiThrowStatement) lastStatement;
            final PsiExpression exception = throwStatement.getException();
            if(!(exception instanceof PsiReferenceExpression)){
                registerError(typeElement);
                return;
            }
            final PsiElement element = ((PsiReference) exception).resolve();
            if(parameter.equals(element)){
                return;
            }
            registerError(typeElement);
        }
    }
}
