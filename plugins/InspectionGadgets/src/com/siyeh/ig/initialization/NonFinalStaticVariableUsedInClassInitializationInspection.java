package com.siyeh.ig.initialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;

public class NonFinalStaticVariableUsedInClassInitializationInspection
                                                                       extends ExpressionInspection{
    public String getDisplayName(){
        return "Non-final static variable is used during class initialization";
    }

    public String getGroupDisplayName(){
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Non-final static variable #ref used during class initialization #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NonFinalStaticVariableUsedInClassInitializationVisitor();
    }

    private static class NonFinalStaticVariableUsedInClassInitializationVisitor
            extends BaseInspectionVisitor{
        public void visitReferenceExpression(PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            if(!isInClassInitialization(expression)){
                return;
            }
            final PsiElement referent = expression.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            if(!field.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            if(field.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            registerError(expression);
        }

        private static boolean isInClassInitialization(PsiExpression expression){
            final PsiClassInitializer initializer =
                    PsiTreeUtil.getParentOfType(expression,
                                                PsiClassInitializer.class);
            if(initializer != null &&
                    initializer.hasModifierProperty(PsiModifier.STATIC)){
                if(!PsiUtil.isOnAssignmentLeftHand(expression)){
                    return true;
                }
            }
            final PsiField field =
                    PsiTreeUtil.getParentOfType(expression, PsiField.class);
            return field != null &&
                    field.hasModifierProperty(PsiModifier.STATIC);
        }
    }
}
