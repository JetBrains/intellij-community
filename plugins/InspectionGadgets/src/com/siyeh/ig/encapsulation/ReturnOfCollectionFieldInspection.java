package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.CollectionUtils;

public class ReturnOfCollectionFieldInspection extends ExpressionInspection{
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
        final PsiType type = field.getType();
        if(type.getArrayDimensions() > 0){
            return "'return' of array field #ref #loc";
        } else{
            return "'return' of Collection field #ref #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ReturnOfCollectionFieldVisitor(this, inspectionManager,
                                                  onTheFly);
    }

    private static class ReturnOfCollectionFieldVisitor
            extends BaseInspectionVisitor{
        private ReturnOfCollectionFieldVisitor(BaseInspection inspection,
                                               InspectionManager inspectionManager,
                                               boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReturnStatement(PsiReturnStatement statement){
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
