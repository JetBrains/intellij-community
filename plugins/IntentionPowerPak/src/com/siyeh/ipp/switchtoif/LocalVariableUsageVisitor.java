package com.siyeh.ipp.switchtoif;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;

class LocalVariableUsageVisitor extends PsiRecursiveElementVisitor{
    private final PsiLocalVariable m_var;
    private boolean m_used = false;

    LocalVariableUsageVisitor(PsiLocalVariable name){
        super();
        m_var = name;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression){
        final PsiElement reference = expression.resolve();
        if(m_var.equals(reference)){
            m_used = true;
        }
        super.visitReferenceElement(expression);
    }

    public boolean isUsed(){
        return m_used;
    }
}
