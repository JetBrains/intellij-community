package com.siyeh.ipp.switchtoif;

import com.intellij.psi.PsiElement;

import java.util.*;

class SwitchStatementBranch{
    private final Set m_pendingVariableDeclarations = new HashSet(5);
    private final List m_labels = new ArrayList(2);
    private final List m_bodyElements = new ArrayList(5);
    private final List m_pendingWhiteSpace = new ArrayList(2);
    private boolean m_default = false;
    private boolean m_hasStatements = false;

    SwitchStatementBranch(){
        super();
    }

    public void addLabel(String labelString){
        m_labels.add(labelString);
    }

    public void addStatement(PsiElement statement){
        m_hasStatements = true;
        addElement(statement);
    }

    public void addComment(PsiElement comment){
        addElement(comment);
    }

    private void addElement(PsiElement element){
        m_bodyElements.addAll(m_pendingWhiteSpace);
        m_pendingWhiteSpace.clear();
        m_bodyElements.add(element);
    }

    public void addWhiteSpace(PsiElement statement){
        if(m_bodyElements.size() > 0){
            m_pendingWhiteSpace.add(statement);
        }
    }

    public List getLabels(){
        return Collections.unmodifiableList(m_labels);
    }

    public List getBodyElements(){
        return Collections.unmodifiableList(m_bodyElements);
    }

    public boolean isDefault(){
        return m_default;
    }

    public void setDefault(){
        m_default = true;
    }

    public boolean hasStatements(){
        return m_hasStatements;
    }

    public void addPendingVariableDeclarations(Set vars){
        m_pendingVariableDeclarations.addAll(vars);
    }

    public Set getPendingVariableDeclarations(){
        return Collections.unmodifiableSet(m_pendingVariableDeclarations);
    }
}
