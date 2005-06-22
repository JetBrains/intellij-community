package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentToStaticFieldFromInstanceMethodInspection
        extends ExpressionInspection{
    public String getDisplayName(){
        return "Assignment to static field from instance method";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Assignment to static field '#ref' from an instance method #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AssignmentToStaticFieldFromInstanceMethod();
    }

    private static class AssignmentToStaticFieldFromInstanceMethod
            extends BaseInspectionVisitor{
        private boolean inClass = false;
        private boolean inInstanceMethod = false;

        public void visitClass(@NotNull PsiClass aClass){
            if(!inClass){
                inClass = true;
                super.visitClass(aClass);
                inClass = false;
            }
        }

        public void visitMethod(@NotNull PsiMethod method){
            final boolean wasInInstanceMethod = inInstanceMethod;
            inInstanceMethod = !method.hasModifierProperty(PsiModifier.STATIC);
            super.visitMethod(method);
            inInstanceMethod = wasInInstanceMethod;
        }

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!inInstanceMethod){
                return;
            }
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForStaticFieldAccess(lhs);
        }

        public void visitPrefixExpression(@NotNull PsiPrefixExpression expression){
            super.visitPrefixExpression(expression);
            if(!inInstanceMethod){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if(sign == null){
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                       !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            checkForStaticFieldAccess(operand);
        }

        public void visitPostfixExpression(@NotNull PsiPostfixExpression expression){
            super.visitPostfixExpression(expression);
            if(!inInstanceMethod){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if(sign == null){
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                       !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            checkForStaticFieldAccess(operand);
        }

        private void checkForStaticFieldAccess(PsiExpression expression){
            if(!(expression instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent = ((PsiReference) expression).resolve();
            if(referent == null){
                return;
            }
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField fieldReferenced = (PsiField) referent;
            if(fieldReferenced.hasModifierProperty(PsiModifier.STATIC)){
                registerError(expression);
            }
        }
    }
}
