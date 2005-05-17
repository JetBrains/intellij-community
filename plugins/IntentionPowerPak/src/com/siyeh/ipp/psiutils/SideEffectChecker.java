package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class SideEffectChecker{
    private SideEffectChecker(){
        super();
    }

    public static boolean mayHaveSideEffects(PsiExpression exp){
        final SideEffectsVisitor visitor = new SideEffectsVisitor();
        exp.accept(visitor);
        return visitor.mayHaveSideEffects();
    }

    private static class SideEffectsVisitor extends PsiRecursiveElementVisitor{
        private boolean mayHaveSideEffects = false;

        public void visitElement(PsiElement element){
            if(!mayHaveSideEffects){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            mayHaveSideEffects = true;
        }

        public void visitNewExpression(PsiNewExpression expression){
            mayHaveSideEffects = true;
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression){
            mayHaveSideEffects = true;
        }

        public void visitPrefixExpression(PsiPrefixExpression expression){
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();

            super.visitPrefixExpression(expression);
            if(tokenType.equals(JavaTokenType.PLUSPLUS) ||
                    tokenType.equals(JavaTokenType.MINUSMINUS)){
                mayHaveSideEffects = true;
            }
        }

        public void visitPostfixExpostssion(PsiPostfixExpression expostssion){
            final PsiJavaToken sign = expostssion.getOperationSign();
            final IElementType tokenType = sign.getTokenType();

            super.visitPostfixExpression(expostssion);
            if(tokenType.equals(JavaTokenType.PLUSPLUS) ||
                    tokenType.equals(JavaTokenType.MINUSMINUS)){
                mayHaveSideEffects = true;
            }
        }

        private boolean mayHaveSideEffects(){
            return mayHaveSideEffects;
        }
    }
}
