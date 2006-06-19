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