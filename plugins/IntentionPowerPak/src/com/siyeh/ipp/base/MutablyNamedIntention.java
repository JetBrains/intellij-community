/*
 * Copyright 2003-2006 Dave Griffith
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
package com.siyeh.ipp.base;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public abstract class MutablyNamedIntention extends Intention{

    private String m_text = null;

    protected abstract String getTextForElement(PsiElement element);

    public String getText(){
        return m_text;
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file){
        final PsiElement element = findMatchingElement(file, editor);
        if(element != null){
            m_text = getTextForElement(element);
        }
        return element != null;
    }
}