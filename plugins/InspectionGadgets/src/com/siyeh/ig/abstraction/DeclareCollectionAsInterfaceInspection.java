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
package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeclareCollectionAsInterfaceInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreLocalVariables = false;
    /** @noinspection PublicField*/
    public boolean ignorePrivateMethodsAndFields = false;

    @NotNull
    public String getID(){
        return "CollectionDeclaredAsConcreteClass";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "collection.declared.by.class.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final String type = ((PsiElement)infos[0]).getText();
        final String interfaceName =
                CollectionUtils.getInterfaceForClass(type);
        return InspectionGadgetsBundle.message(
                "collection.declared.by.class.problem.descriptor",
                interfaceName);
    }

    @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "collection.declared.by.class.ignore.locals.option"),
                "ignoreLocalVariables");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "collection.declared.by.class.ignore.private.members.option"),
                "ignorePrivateMethodsAndFields");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DeclareCollectionAsInterfaceVisitor();
    }

    private class DeclareCollectionAsInterfaceVisitor
            extends BaseInspectionVisitor {

        @Override public void visitVariable(@NotNull PsiVariable variable) {
            if (ignoreLocalVariables && variable instanceof PsiLocalVariable) {
                return;
            }
            if (ignorePrivateMethodsAndFields) {
                if (variable instanceof PsiField) {
                    if (variable.hasModifierProperty(PsiModifier.PRIVATE)) {
                        return;
                    }
                }
            }
            if (variable instanceof PsiParameter) {
                final PsiParameter parameter = (PsiParameter)variable;
                final PsiElement scope = parameter.getDeclarationScope();
                if (scope instanceof PsiMethod) {
                    if (ignorePrivateMethodsAndFields) {
                        final PsiMethod method = (PsiMethod)scope;
                        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                            return;
                        }
                    }
                } else if (ignoreLocalVariables) {
                    return;
                }
            }
            final PsiType type = variable.getType();
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiJavaCodeReferenceElement reference =
                    typeElement.getInnermostComponentReferenceElement();
            if (reference == null) {
                return;
            }
            final PsiElement nameElement = reference.getReferenceNameElement();
            if (nameElement == null) {
                return;
            }
            registerError(nameElement, nameElement);
        }

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (ignorePrivateMethodsAndFields &&
                    method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiType type = method.getReturnType();
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiJavaCodeReferenceElement referenceElement =
                    typeElement.getInnermostComponentReferenceElement();
            if (referenceElement == null) {
                return;
            }
            final PsiElement nameElement =
                    referenceElement.getReferenceNameElement();
            if (nameElement == null) {
                return;
            }
            registerError(nameElement, nameElement);
        }
    }
}