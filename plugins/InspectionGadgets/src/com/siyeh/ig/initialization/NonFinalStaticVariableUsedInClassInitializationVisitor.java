/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.09.2006
 * Time: 18:38:52
 */
package com.siyeh.ig.initialization;

import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

class NonFinalStaticVariableUsedInClassInitializationVisitor extends BaseInspectionVisitor {

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

    private static boolean isInClassInitialization(
            PsiExpression expression){
        final PsiClass expressionClass =
                PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        final PsiMember member =
                PsiTreeUtil.getParentOfType(expression,
                        PsiClassInitializer.class, PsiField.class);
        if (member == null) {
            return false;
        }
        final PsiClass memberClass = member.getContainingClass();
        if (!memberClass.equals(expressionClass)) {
            return false;
        }
        if (!member.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (member instanceof PsiClassInitializer) {
            return !PsiUtil.isOnAssignmentLeftHand(expression);
        }
        return true;
    }
}