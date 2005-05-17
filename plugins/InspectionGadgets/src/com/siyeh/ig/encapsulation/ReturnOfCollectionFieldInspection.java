package com.siyeh.ig.encapsulation;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class ReturnOfCollectionFieldInspection extends StatementInspection{
    public String getID(){
        return "ReturnOfCollectionOrArrayField";
    }

    public String getDisplayName(){
        return "Return of Collection or array field";
    }

    public String getGroupDisplayName(){
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiField field = (PsiField) ((PsiReference) location).resolve();
        assert field != null;
        final PsiType type = field.getType();
        if(type.getArrayDimensions() > 0){
            return "'return' of array field #ref #loc";
        } else{
            return "'return' of Collection field #ref #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReturnOfCollectionFieldVisitor();
    }

    private static class ReturnOfCollectionFieldVisitor
            extends StatementInspectionVisitor{


        public void visitReturnStatement(@NotNull PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if(returnValue == null){
                return;
            }
            if(!CollectionUtils.isArrayOrCollectionField(returnValue)){
                return;
            }
            registerError(returnValue);
        }
    }
}
