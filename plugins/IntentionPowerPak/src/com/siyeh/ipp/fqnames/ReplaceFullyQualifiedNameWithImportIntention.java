/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import com.siyeh.ipp.psiutils.ImportUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ReplaceFullyQualifiedNameWithImportIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new FullyQualifiedNamePredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiJavaCodeReferenceElement reference =
                (PsiJavaCodeReferenceElement)element;
        PsiElement target = reference.resolve();
        if (!(target instanceof PsiClass)) {
            while (reference.getParent() instanceof PsiJavaCodeReferenceElement) {
                reference = (PsiJavaCodeReferenceElement)reference.getParent();
                target = reference.resolve();
                if (target instanceof PsiClass) {
                    break;
                }
            }
        }
        if (!(target instanceof PsiClass)) {
            return;
        }
        final PsiClass aClass = (PsiClass)target;
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        final PsiJavaFile file =
                PsiTreeUtil.getParentOfType(reference, PsiJavaFile.class);
        if (file == null) {
            return;
        }
        if (!ImportUtils.nameCanBeImported(qualifiedName, file)) {
            return;
        }
        final PsiImportList importList = file.getImportList();
        if (importList == null) {
            return;
        }
        @NonNls final String packageName =
                ClassUtil.extractPackageName(qualifiedName);
        final String filePackageName = file.getPackageName();
        if (packageName.equals("java.lang") ||
            packageName.equals(filePackageName)) {
            if (ImportUtils.hasOnDemandImportConflict(qualifiedName,
                    file)) {
                addImport(importList, aClass);
            }
        } else if (importList.findSingleClassImportStatement(
                qualifiedName) == null &&
                                       importList.findOnDemandImportStatement(
                                               packageName) == null) {
            addImport(importList, aClass);
        }
        final String fullyQualifiedText = reference.getText();
        final QualificationRemover qualificationRemover =
                new QualificationRemover(fullyQualifiedText);
        file.accept(qualificationRemover);
        final Collection<PsiJavaCodeReferenceElement> shortenedElements =
                qualificationRemover.getShortenedElements();
        HighlightUtil.highlightElements(shortenedElements);
        showStatusMessage(file.getProject(), shortenedElements.size());
    }

    private static void showStatusMessage(Project project, int elementCount) {
      final String text;
        if (elementCount == 1) {
            text = IntentionPowerPackBundle.message(
                    "1.fully.qualified.name.status.bar.escape.highlighting.message");
        } else {
            text = IntentionPowerPackBundle.message(
                    "multiple.fully.qualified.names.status.bar.escape.highlighting.message",
                              Integer.valueOf(elementCount - 1));
        }

      StatusBar.Info.set(text, project);
    }

    private static void addImport(PsiImportList importList, PsiClass aClass)
            throws IncorrectOperationException {
        final PsiManager manager = importList.getManager();
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final PsiImportStatement importStatement =
                elementFactory.createImportStatement(aClass);
        importList.add(importStatement);
    }

    private static class QualificationRemover
            extends JavaRecursiveElementWalkingVisitor {

        private final String fullyQualifiedText;
        private final List<PsiJavaCodeReferenceElement> shortenedElements =
                new ArrayList();

        QualificationRemover(String fullyQualifiedText) {
            this.fullyQualifiedText = fullyQualifiedText;
        }

        public Collection<PsiJavaCodeReferenceElement> getShortenedElements() {
            return Collections.unmodifiableCollection(shortenedElements);
        }

        @Override public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            final PsiElement parent = reference.getParent();
            if (parent instanceof PsiImportStatement) {
                return;
            }
            final String text = reference.getText();
            if (text.equals(fullyQualifiedText)) {
                final PsiElement qualifier = reference.getQualifier();
                if (qualifier == null) {
                    return;
                }
                try {
                    qualifier.delete();
                } catch(IncorrectOperationException e){
                    final Class<? extends QualificationRemover> aClass =
                            getClass();
                    final String className = aClass.getName();
                    final Logger logger = Logger.getInstance(className);
                    logger.error(e);
                }
                shortenedElements.add(reference);
            }
        }
    }
}