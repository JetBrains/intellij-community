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
package com.siyeh.ipp.fqnames;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.siyeh.ipp.base.PsiElementPredicate;

class FullyQualifiedNamePredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaCodeReferenceElement)) {
            return false;
        }
        final PsiJavaCodeReferenceElement referenceElement =
                (PsiJavaCodeReferenceElement)element;
        if(!referenceElement.isQualified()) {
            return false;
        }
        if (PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class,
                PsiPackageStatement.class) != null) {
            return false;
        }
        final PsiElement qualifier = referenceElement.getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement) {
            final PsiJavaCodeReferenceElement qualfierReferenceElement =
                    (PsiJavaCodeReferenceElement)qualifier;
            final PsiElement resolved = qualfierReferenceElement.resolve();
            if (resolved instanceof PsiPackage) {
                return true;
            }
            if (resolved instanceof PsiClass) {
                final Project project = element.getProject();
                final CodeStyleSettings codeStyleSettings =
                        CodeStyleSettingsManager.getSettings(project);
                return codeStyleSettings.INSERT_INNER_CLASS_IMPORTS;
            }
        }
        return false;
    }
}