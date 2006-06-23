/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.imports;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class ReplaceOnDemandImportIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new OnDemandImportPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiImportStatementBase importStatementBase =
                (PsiImportStatementBase)element;
        if (importStatementBase instanceof PsiImportStatement) {
            final PsiImportStatement importStatement =
                    (PsiImportStatement)importStatementBase;
            final PsiJavaFile javaFile =
                    (PsiJavaFile)importStatement.getContainingFile();
            final PsiClass[] classes = javaFile.getClasses();
            final String qualifiedName = importStatement.getQualifiedName();
            final ClassCollector visitor = new ClassCollector(qualifiedName);
            for (PsiClass aClass : classes) {
                aClass.accept(visitor);
            }
            final PsiClass[] importedClasses = visitor.getImportedClasses();
            Arrays.sort(importedClasses, new PsiClassComparator());
            final PsiManager manager = importStatement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiElement importList = importStatement.getParent();
            for (PsiClass importedClass : importedClasses) {
                final PsiImportStatement newImportStatement =
                        factory.createImportStatement(importedClass);
                importList.add(newImportStatement);
            }
            importStatement.delete();
        } else if (importStatementBase instanceof PsiImportStaticStatement) {
            // do something else
        }
    }

    private static class ClassCollector extends PsiRecursiveElementVisitor {

        private final String importedPackageName;
        private final Set<PsiClass> importedClasses = new HashSet();

        ClassCollector(String importedPackageName) {
            this.importedPackageName = importedPackageName;
        }

        public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            if (reference.isQualified()) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass)element;
            final String qualifiedName = aClass.getQualifiedName();
            final String packageName =
                    ClassUtil.extractPackageName(qualifiedName);
            if (!importedPackageName.equals(packageName)) {
                return;
            }
            importedClasses.add(aClass);
        }

        public PsiClass[] getImportedClasses() {
            return importedClasses.toArray(new PsiClass[importedClasses.size()]);
        }
    }

    private static final class PsiClassComparator
            implements Comparator<PsiClass> {

        public int compare(PsiClass class1, PsiClass class2) {
            final String qualifiedName1 = class1.getQualifiedName();
            final String qualifiedName2 = class2.getQualifiedName();
            if (qualifiedName1 == null) {
                return -1;
            }
            return qualifiedName1.compareTo(qualifiedName2);
        }
    }
}