package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;

class LabelSearchVisitor extends PsiRecursiveElementVisitor
{
    private final String m_labelName;
    private boolean m_used = false;

    LabelSearchVisitor(String name)
    {
        super();
        m_labelName = name;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression)
    {

    }

    public void visitLabeledStatement(PsiLabeledStatement statement)
    {
        final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
        final String labelText = labelIdentifier.getText();
        if(labelText.equals(m_labelName))
        {
            m_used = true;
        }
    }

    public boolean isUsed()
    {
        return m_used;
    }

}
