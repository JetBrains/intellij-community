package com.siyeh.ipp.switchtoif;

import com.intellij.psi.PsiStatement;

import java.util.*;

class IfStatementBranch
{
    private Set m_topLevelVariables = new HashSet(3);
    private Set m_innerVariables = new HashSet(3);
    private final List m_conditions = new ArrayList(3);
    private PsiStatement m_statement = null;
    private boolean m_else = false;

    IfStatementBranch()
    {
        super();
    }

    public void addCondition(String conditionString)
    {
        m_conditions.add(conditionString);
    }

    public void setStatement(PsiStatement statement)
    {
        m_statement = statement;
    }

    public PsiStatement getStatement()
    {
        return m_statement;
    }

    public List getConditions()
    {
        return Collections.unmodifiableList(m_conditions);
    }

    public boolean isElse()
    {
        return m_else;
    }

    public void setElse()
    {
        m_else = true;
    }

    public void setTopLevelVariables(Set topLevelVariables)
    {
        m_topLevelVariables = new HashSet(topLevelVariables);
    }

    public void setInnerVariables(Set innerVariables)
    {
        m_innerVariables =  new HashSet(innerVariables);
    }

    private Set getTopLevelVariables()
    {
        return Collections.unmodifiableSet(m_topLevelVariables);
    }

    private Set getInnerVariables()
    {
        return Collections.unmodifiableSet(m_innerVariables);
    }

    public boolean topLevelDeclarationsConfictWith(IfStatementBranch testBranch)
    {
        final Set innerVariables = testBranch.getInnerVariables();
        final Set topLevel = testBranch.getTopLevelVariables();
        return hasNonEmptyIntersection(m_topLevelVariables, topLevel) ||
                hasNonEmptyIntersection(m_topLevelVariables, innerVariables);
    }

    private static boolean hasNonEmptyIntersection(Set set1, Set set2)
    {
        for(Iterator iterator = set1.iterator(); iterator.hasNext();)
        {
            final Object set1Element = iterator.next();
            if(set2.contains(set1Element))
            {
                return true;
            }
        }
        return false;
    }

}
