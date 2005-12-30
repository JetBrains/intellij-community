/*
 * Copyright 2005 Bas Leijdekkers
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
package com.siyeh.ig.imports;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class StaticImportIsUsedVisitor extends PsiRecursiveElementVisitor {

    private final PsiImportStaticStatement m_import;
    private boolean m_used = false;

    StaticImportIsUsedVisitor(PsiImportStaticStatement importStatement) {
        super();
        m_import = importStatement;
    }

    public void visitElement(PsiElement element) {
        if (m_used) {
            return;
        }
        super.visitElement(element);
    }

    public void visitReferenceElement(
            @NotNull PsiJavaCodeReferenceElement reference) {
        followReferenceToImport(reference);
        super.visitReferenceElement(reference);
    }

    private void followReferenceToImport(
            PsiJavaCodeReferenceElement reference) {
        if (reference.getQualifier()!=null) {
            //it's already fully qualified, so the import statement wasn't
            // responsible
            return;
        }
        final String referenceName = m_import.getReferenceName();
        final String qualifiedName = reference.getQualifiedName();
        if (referenceName != null && !referenceName.equals(qualifiedName)) {
            return;
        }
        final PsiClass targetClass =
                m_import.resolveTargetClass();
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiMember)) {
            return;
        }
        final PsiMember member = (PsiMember)element;
        final PsiClass containingClass = member.getContainingClass();
        if (containingClass != null && containingClass.equals(targetClass)) {
            m_used = true;
        }
    }

    public boolean isUsed() {
        return m_used;
    }
}