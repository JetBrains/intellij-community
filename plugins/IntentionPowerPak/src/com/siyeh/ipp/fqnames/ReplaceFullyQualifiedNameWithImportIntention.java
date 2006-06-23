/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ImportUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceFullyQualifiedNameWithImportIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new FullyQualifiedNamePredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiJavaCodeReferenceElement reference =
                (PsiJavaCodeReferenceElement)element;
        while (reference.getParent() instanceof PsiJavaCodeReferenceElement) {
            reference = (PsiJavaCodeReferenceElement)reference.getParent();
        }
        final PsiJavaFile javaFile =
                PsiTreeUtil.getParentOfType(reference, PsiJavaFile.class);
        if (javaFile == null) {
            return;
        }
        final PsiElement target = reference.resolve();
        if(!(target instanceof PsiClass)){
            return;
        }
        final PsiClass aClass = (PsiClass)target;
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (!ImportUtils.nameCanBeImported(qualifiedName, javaFile)) {
            return;
        }
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        @NonNls final String packageName =
                ClassUtil.extractPackageName(qualifiedName);
        if (packageName.equals("java.lang")) {
            if (ImportUtils.hasOnDemandImportConflict(qualifiedName,
                    javaFile)) {
                addImport(importList, aClass);
            }
        } else if (
                importList.findSingleClassImportStatement(qualifiedName) ==
                        null) {
            addImport(importList, aClass);
        }
        final PsiElement qualifier = reference.getQualifier();
        if (qualifier == null) {
            return;
        }
        qualifier.delete();
    }

    private static void addImport(PsiImportList importList, PsiClass aClass)
            throws IncorrectOperationException {
        final PsiManager manager = importList.getManager();
        final PsiElementFactory elementFactory =
                manager.getElementFactory();
        final PsiImportStatement importStatement =
                elementFactory.createImportStatement(aClass);
        importList.add(importStatement);
    }
}
