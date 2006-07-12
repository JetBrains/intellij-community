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
package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class RawUseOfParameterizedTypeInspection extends VariableInspection {

    /** @noinspection PublicField*/
    public boolean ignoreObjectConstruction = true;

    /** @noinspection PublicField*/
    public boolean ignoreTypeCasts = false;


    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "raw.use.of.parameterized.type.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "raw.use.of.parameterized.type.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
                InspectionGadgetsBundle.message(
                        "raw.use.of.parameterized.type.ignore.new.objects.option"),
                "ignoreObjectConstruction");
        optionsPanel.addCheckbox(
                InspectionGadgetsBundle.message(
                        "raw.use.of.parameterized.type.ignore.type.casts.option"),
                "ignoreTypeCasts");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RawUseOfParameterizedTypeVisitor();
    }

    private class RawUseOfParameterizedTypeVisitor
            extends BaseInspectionVisitor {

        public void visitElement(PsiElement element) {
            final LanguageLevel languageLevel =
                    PsiUtil.getLanguageLevel(element);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            super.visitElement(element);
        }

        public void visitNewExpression(
                @NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (ignoreObjectConstruction) {
                return;
            }
            if (expression.getArrayInitializer() != null ||
                expression.getArrayDimensions().length > 0) {
                // array creation cannot be generic
                return;
            }
            final PsiJavaCodeReferenceElement classReference =
                    expression.getClassReference();
            checkReferenceElement(classReference);
        }

        public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
            super.visitTypeElement(typeElement);
            final PsiElement parent = typeElement.getParent();
            if (parent instanceof PsiInstanceOfExpression ||
                    parent instanceof PsiClassObjectAccessExpression) {
                return;
            }
            if (ignoreTypeCasts && parent instanceof PsiTypeCastExpression) {
                return;
            }
            if (PsiTreeUtil.getParentOfType(typeElement, PsiComment.class)
                    != null) {
                return;
            }
            final PsiJavaCodeReferenceElement referenceElement =
                    typeElement.getInnermostComponentReferenceElement();
            checkReferenceElement(referenceElement);
        }

        public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            final PsiElement referenceParent = reference.getParent();
            if (!(referenceParent instanceof PsiReferenceList)) {
                return;
            }
            final PsiReferenceList referenceList =
                    (PsiReferenceList)referenceParent;
            final PsiElement listParent = referenceList.getParent();
            if (!(listParent instanceof PsiClass)) {
                return;
            }
            checkReferenceElement(reference);
        }

        private void checkReferenceElement(
                @Nullable PsiJavaCodeReferenceElement reference) {
            if (reference == null) {
                return;
            }
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement) {
                final PsiJavaCodeReferenceElement qualifierReference =
                        (PsiJavaCodeReferenceElement)qualifier;
                checkReferenceElement(qualifierReference);
            }
            final PsiType[] typeParameters = reference.getTypeParameters();
            if (typeParameters.length > 0) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass)element;
            if (!aClass.hasTypeParameters()) {
                return;
            }
            registerError(reference);
        }
    }
}