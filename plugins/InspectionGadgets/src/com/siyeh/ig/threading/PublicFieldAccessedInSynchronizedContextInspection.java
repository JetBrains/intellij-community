package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class PublicFieldAccessedInSynchronizedContextInspection
        extends ExpressionInspection{
    public String getID(){
        return "NonPrivateFieldAccessedInSynchronizedContext";
    }

    public String getDisplayName(){
        return "Non-private field accessed in synchronized context";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Non-private field #ref accessed in synchronized context  #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new PublicFieldAccessedInSynchronizedContextVisitor(this,
                                                                   inspectionManager,
                                                                   onTheFly);
    }

    private static class PublicFieldAccessedInSynchronizedContextVisitor
            extends BaseInspectionVisitor{
        private boolean m_inSynchronizedContext = false;

        private PublicFieldAccessedInSynchronizedContextVisitor(BaseInspection inspection,
                                                                InspectionManager inspectionManager,
                                                                boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            if(!m_inSynchronizedContext){
                return;
            }
            final PsiElement element = expression.resolve();
            if(!(element instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) element;
            if(field.hasModifierProperty(PsiModifier.PRIVATE) ||
                       field.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            registerError(expression);
        }

        public void visitMethod(PsiMethod method){
            final boolean wasInSynchronizedContext = m_inSynchronizedContext;
            if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){

                m_inSynchronizedContext = true;
            }
            super.visitMethod(method);
            if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){

                m_inSynchronizedContext = wasInSynchronizedContext;
            }
        }

        public void visitSynchronizedStatement(PsiSynchronizedStatement psiSynchronizedStatement){
            final boolean wasInSynchronizedContext = m_inSynchronizedContext;
            m_inSynchronizedContext = true;
            super.visitSynchronizedStatement(psiSynchronizedStatement);
            m_inSynchronizedContext = wasInSynchronizedContext;
        }
    }
}
