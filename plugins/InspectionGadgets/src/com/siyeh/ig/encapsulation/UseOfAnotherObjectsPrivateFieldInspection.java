package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class UseOfAnotherObjectsPrivateFieldInspection extends ExpressionInspection {
    public String getID(){
        return "AccessingNonPublicFieldOfAnotherObject";
    }
    public String getDisplayName() {
        return "Accessing a non-public field of another object";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Direct access of non-public field #ref on another object #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UseOfAnotherObjectsPrivateFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class UseOfAnotherObjectsPrivateFieldVisitor extends BaseInspectionVisitor {
        private UseOfAnotherObjectsPrivateFieldVisitor(BaseInspection inspection,
                                                                InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            final PsiExpression qualifier = expression.getQualifierExpression();
            if(qualifier == null || qualifier instanceof PsiThisExpression){
                return;
            }
            final PsiElement referent = expression.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            if(!field.hasModifierProperty(PsiModifier.PRIVATE) &&
                       !field.hasModifierProperty(PsiModifier.PROTECTED)){
                return;
            }
            final PsiElement fieldNameElement =
                    expression.getReferenceNameElement();
            registerError(fieldNameElement);
        }
    }

}
