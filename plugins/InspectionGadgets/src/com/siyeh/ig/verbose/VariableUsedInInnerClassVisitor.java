package com.siyeh.ig.verbose;

import com.intellij.psi.*;

public class VariableUsedInInnerClassVisitor extends PsiRecursiveElementVisitor
{
    private final PsiVariable m_variable;
    private boolean m_usedInInnerClass = false;
    private boolean m_inInnerClass = false;

    public VariableUsedInInnerClassVisitor(PsiVariable variable)
    {
        super();
        m_variable = variable;
    }

    public void visitAnonymousClass(PsiAnonymousClass psiAnonymousClass)
    {
        final boolean wasInInnerClass = m_inInnerClass;
        m_inInnerClass = true;
        super.visitAnonymousClass(psiAnonymousClass);
        m_inInnerClass = wasInInnerClass;
    }

    public void visitReferenceExpression(PsiReferenceExpression reference)
    {
        super.visitReferenceExpression(reference);
        if(m_inInnerClass)
        {
            final PsiElement element = reference.resolve();
            if(m_variable.equals(element))
            {
                m_usedInInnerClass = true;
            }
        }
    }

    public boolean isUsedInInnerClass()
    {
        return m_usedInInnerClass;
    }
}
