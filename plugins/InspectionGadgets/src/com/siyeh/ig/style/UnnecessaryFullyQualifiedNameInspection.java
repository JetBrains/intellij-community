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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.HighlightUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UnnecessaryFullyQualifiedNameInspection extends BaseInspection {

    @SuppressWarnings("PublicField")
    public boolean m_ignoreJavadoc = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.fully.qualified.name.display.name");
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "unnecessary.fully.qualified.name.ignore.option"),
                this, "m_ignoreJavadoc");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "unnecessary.fully.qualified.name.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new UnnecessaryFullyQualifiedNameFix();
    }

    private static class UnnecessaryFullyQualifiedNameFix
            extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "unnecessary.fully.qualified.name.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
            final PsiJavaFile file =
                    (PsiJavaFile)referenceElement.getContainingFile();
            if (file == null) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            if (importList == null) {
                return;
            }
            final PsiClass aClass = (PsiClass)referenceElement.resolve();
            if (aClass == null) {
                return;
            }
            final String qualifiedName = aClass.getQualifiedName();
            if (qualifiedName == null) {
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
            } else if (importList.findSingleClassImportStatement(qualifiedName)
                       == null
                       && importList.findOnDemandImportStatement(packageName)
                          == null) {
                addImport(importList, aClass);
            }
            final String fullyQualifiedText = referenceElement.getText();
            final QualificationRemover qualificationRemover =
                    new QualificationRemover(fullyQualifiedText);
            file.accept(qualificationRemover);
            final Collection<PsiElement> shortenedElements =
                    qualificationRemover.getShortenedElements();
            HighlightUtil.highlightElements(shortenedElements);
            showStatusMessage(file.getProject(), shortenedElements.size());
        }

        private static void showStatusMessage(Project project,
                                              int elementCount) {
            final WindowManager windowManager = WindowManager.getInstance();
            final StatusBar statusBar = windowManager.getStatusBar(project);
            if (elementCount == 1) {
                statusBar.setInfo(InspectionGadgetsBundle.message(
                        "unnecessary.fully.qualified.name.status.bar.escape.highlighting.message1"));
            } else {
                statusBar.setInfo(InspectionGadgetsBundle.message(
                        "unnecessary.fully.qualified.name.status.bar.escape.highlighting.message2",
                        Integer.valueOf(elementCount - 1)));
            }
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

        private static class QualificationRemover
                extends PsiRecursiveElementVisitor {

            private final String fullyQualifiedText;
            private final List<PsiElement> shortenedElements =
                    new ArrayList();

            QualificationRemover(String fullyQualifiedText) {
                this.fullyQualifiedText = fullyQualifiedText;
            }

            public Collection<PsiElement> getShortenedElements() {
                return Collections.unmodifiableCollection(shortenedElements);
            }

            public void visitReferenceElement(
                    PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);
                final PsiElement parent = PsiTreeUtil.getParentOfType(reference,
                        PsiImportStatementBase.class);
                if (parent != null) {
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

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryFullyQualifiedNameVisitor();
    }

    private class UnnecessaryFullyQualifiedNameVisitor
            extends BaseInspectionVisitor{

        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            checkReference(expression);
        }

        public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            checkReference(reference);
        }

        private void checkReference(PsiJavaCodeReferenceElement reference) {
            if (!reference.isQualified()) {
                return;
            }
            final PsiElement parent = reference.getParent();
            if (parent instanceof PsiMethodCallExpression ||
                parent instanceof PsiAssignmentExpression ||
                parent instanceof PsiVariable) {
                return;
            }
            final PsiElement element = PsiTreeUtil.getParentOfType(reference,
                    PsiImportStatementBase.class, PsiPackageStatement.class,
                    PsiCodeFragment.class);
            if (element != null) {
                return;
            }
            if(m_ignoreJavadoc){
                final PsiElement containingComment =
                        PsiTreeUtil.getParentOfType(reference,
                                PsiDocComment.class);
                if(containingComment != null){
                    return;
                }
            }
            final PsiElement psiElement = reference.resolve();
            if(!(psiElement instanceof PsiClass)){
                return;
            }
            PsiClass aClass = (PsiClass) psiElement;
            final Project project = aClass.getProject();
            final CodeStyleSettings styleSettings =
                    CodeStyleSettingsManager.getSettings(project);
            if (!styleSettings.INSERT_INNER_CLASS_IMPORTS) {
                aClass = ClassUtils.getOutermostContainingClass(aClass);
            }
            final String fqName = aClass.getQualifiedName();
            if (fqName == null) {
                return;
            }
            final String text = stripAngleBrackets(reference.getText());
            if(!text.equals(fqName)){
                return;
            }
            final PsiJavaFile javaFile =
                    PsiTreeUtil.getParentOfType(reference, PsiJavaFile.class);
            if (javaFile == null) {
                return;
            }
            if (!ImportUtils.nameCanBeImported(fqName, javaFile)) {
                return;
            }
            registerError(reference);
        }

        private String stripAngleBrackets(String string) {
            final int index = string.indexOf('<');
            if (index == -1) {
                return string;
            }
            return string.substring(0, index);
        }
    }
}