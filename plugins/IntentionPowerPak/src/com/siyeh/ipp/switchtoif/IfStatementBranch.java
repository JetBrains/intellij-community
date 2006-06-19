/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.switchtoif;

import com.intellij.psi.PsiStatement;

import java.util.*;

class IfStatementBranch{

    private Set<String> m_topLevelVariables = new HashSet<String>(3);
    private Set<String> m_innerVariables = new HashSet<String>(3);
    private final List<String> m_conditions = new ArrayList<String>(3);
    private PsiStatement m_statement = null;
    private boolean m_else = false;

    public void addCondition(String conditionString){
        m_conditions.add(conditionString);
    }

    public void setStatement(PsiStatement statement){
        m_statement = statement;
    }

    public PsiStatement getStatement(){
        return m_statement;
    }

    public List<String> getConditions(){
        return Collections.unmodifiableList(m_conditions);
    }

    public boolean isElse(){
        return m_else;
    }

    public void setElse(){
        m_else = true;
    }

    public void setTopLevelVariables(Set<String> topLevelVariables){
        m_topLevelVariables = new HashSet<String>(topLevelVariables);
    }

    public void setInnerVariables(Set<String> innerVariables){
        m_innerVariables = new HashSet<String>(innerVariables);
    }

    private Set<String> getTopLevelVariables(){
        return Collections.unmodifiableSet(m_topLevelVariables);
    }

    private Set<String> getInnerVariables(){
        return Collections.unmodifiableSet(m_innerVariables);
    }

    public boolean topLevelDeclarationsConfictWith(
            IfStatementBranch testBranch){
        final Set<String> innerVariables = testBranch.getInnerVariables();
        final Set<String> topLevel = testBranch.getTopLevelVariables();
        return hasNonEmptyIntersection(m_topLevelVariables, topLevel) ||
                hasNonEmptyIntersection(m_topLevelVariables,
                                        innerVariables);
    }

    private static boolean hasNonEmptyIntersection(Set<String> set1,
                                                   Set<String> set2){
        for(final String set1Element : set1){
            if(set2.contains(set1Element)){
                return true;
            }
        }
        return false;
    }
}
