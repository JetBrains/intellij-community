package com.siyeh.ig.encapsulation;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ReturnOfDateFieldInspection extends StatementInspection{
    public String getDisplayName(){
        return "Return of Date or Calendar field";
    }

    public String getGroupDisplayName(){
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiField field = (PsiField) ((PsiReference) location).resolve();
        assert field != null;
        final PsiType type = field.getType();
        return "'return' of " + type.getPresentableText() + " field #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReturnOfDateFieldVisitor();
    }

    private static class ReturnOfDateFieldVisitor extends StatementInspectionVisitor{

        public void visitReturnStatement(@NotNull PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if(!(returnValue instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression fieldReference =
                    (PsiReferenceExpression) returnValue;

            final PsiElement element = fieldReference.resolve();
            if(!(element instanceof PsiField)){
                return;
            }
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.Date",
                                                     returnValue) &&
                                                                        !TypeUtils.expressionHasTypeOrSubtype("java.util.Calendar",
                                                                                                              returnValue)){
                return;
            }
            registerError(returnValue);
        }
    }
}
