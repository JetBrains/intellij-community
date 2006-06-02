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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class UnnecessaryFullyQualifiedNameInspection extends ClassInspection{

    @SuppressWarnings("PublicField")
    public boolean m_ignoreJavadoc = false;

    public String getGroupDisplayName(){
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.fully.qualified.name.display.name");
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
          com.siyeh.InspectionGadgetsBundle.message(
                  "unnecessary.fully.qualified.name.ignore.option"),
                this, "m_ignoreJavadoc");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "unnecessary.fully.qualified.name.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryFullyQualifiedNameVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new UnnecessaryFullyQualifiedNameFix();
    }

    private static class UnnecessaryFullyQualifiedNameFix
            extends InspectionGadgetsFix{

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
            final PsiImportList importList = file.getImportList();
            if (importList == null) {
                return;
            }
            final PsiClass aClass = (PsiClass)referenceElement.resolve();
            final PsiManager manager = referenceElement.getManager();
            final PsiElementFactory elementFactory =
                    manager.getElementFactory();
            final PsiImportStatement importStatement =
                    elementFactory.createImportStatement(aClass);
            final PsiElement nameElement =
                    referenceElement.getReferenceNameElement();
            if (nameElement == null) {
                return;
            }
            importList.add(importStatement);
            referenceElement.replace(nameElement);

        }
    }

    private class UnnecessaryFullyQualifiedNameVisitor
            extends BaseInspectionVisitor{

        public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference){
            super.visitReferenceElement(reference);
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
            final PsiClass aClass = (PsiClass) psiElement;
            final PsiClass outerClass =
                    ClassUtils.getOutermostContainingClass(aClass);
            final String fqName = outerClass.getQualifiedName();
            final String text = reference.getText();
            if(!text.startsWith(fqName)){
                return;
            }
            final PsiJavaFile javaFile =
                    PsiTreeUtil.getParentOfType(reference, PsiJavaFile.class);
            if (!ImportUtils.nameCanBeImported(fqName, javaFile)) {
                return;
            }
            registerError(reference);
        }
    }
}