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

import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;

class LabelSearchVisitor extends PsiRecursiveElementVisitor{

    private final String m_labelName;
    private boolean m_used = false;

    LabelSearchVisitor(String name){
        super();
        m_labelName = name;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression){
    }

    public void visitLabeledStatement(PsiLabeledStatement statement){
        final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
        final String labelText = labelIdentifier.getText();
        if(labelText.equals(m_labelName)){
            m_used = true;
        }
    }

    public boolean isUsed(){
        return m_used;
    }
}