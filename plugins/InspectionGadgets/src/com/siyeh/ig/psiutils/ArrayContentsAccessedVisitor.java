package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class ArrayContentsAccessedVisitor extends PsiRecursiveElementVisitor{
    private boolean accessed = false;
    private final PsiVariable variable;

    public ArrayContentsAccessedVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitForeachStatement(PsiForeachStatement statement){
        if(accessed){
            return;
        }
        super.visitForeachStatement(statement);
        final PsiExpression qualifier = statement.getIteratedValue();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) qualifier).resolve();
        if(referent == null){
            return;
        }
        if(!referent.equals(variable)){
            return;
        }
        accessed = true;
    }

    public void visitArrayAccessExpression(PsiArrayAccessExpression arg){
        if(accessed){
            return;
        }
        super.visitArrayAccessExpression(arg);
        if(arg.getParent() instanceof PsiAssignmentExpression &&
                        ((PsiAssignmentExpression) arg.getParent()).getLExpression()
                                .equals(arg)){
            return;
        }
        final PsiExpression arrayExpression = arg.getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            accessed = true;
        }
    }

    public boolean isAccessed(){
        return accessed;
    }
}
