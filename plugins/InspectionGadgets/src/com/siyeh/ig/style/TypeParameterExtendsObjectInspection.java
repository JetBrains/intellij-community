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
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class TypeParameterExtendsObjectInspection extends ClassInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "type.parameter.extends.object.display.name");
    }

    @NotNull
    public String getID() {
        return "TypeParameterExplicitlyExtendsObject";
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final Integer type = (Integer) infos[0];
        if (type.intValue() == 1) {
        return InspectionGadgetsBundle.message(
                "type.parameter.extends.object.problem.descriptor1");
        } else {
            return InspectionGadgetsBundle.message(
                    "type.parameter.extends.object.problem.descriptor2");
        }
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new ExtendsObjectFix();
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "extends.object.remove.quickfix");
        }

        public void doFix(@NotNull Project project,
                          ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiElement parent = identifier.getParent();
            if (parent instanceof PsiTypeParameter) {
                final PsiTypeParameter typeParameter =
                        (PsiTypeParameter) parent;
                final PsiReferenceList extendsList =
                        typeParameter.getExtendsList();
                final PsiJavaCodeReferenceElement[] referenceElements =
                        extendsList.getReferenceElements();
                for(PsiJavaCodeReferenceElement referenceElement :
                        referenceElements){
                    deleteElement(referenceElement);
                }
            } else {
                final PsiTypeElement typeElement = (PsiTypeElement) parent;
                PsiElement child = typeElement.getLastChild();
                while (child != null) {
                    if (child instanceof PsiJavaToken) {
                        final PsiJavaToken javaToken = (PsiJavaToken)child;
                        final IElementType tokenType = javaToken.getTokenType();
                        if (tokenType == JavaTokenType.QUEST) {
                            return;
                        }
                    }
                    child.delete();
                    child = typeElement.getLastChild();
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExtendsObjectVisitor();
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

        public void visitTypeParameter(PsiTypeParameter parameter){
            super.visitTypeParameter(parameter);
            final PsiClassType[] extendsListTypes =
                    parameter.getExtendsListTypes();
            if (extendsListTypes.length != 1) {
                return;
            }
            final PsiClassType extendsType = extendsListTypes[0];
            if (!extendsType.equalsToText("java.lang.Object")) {
                return;
            }
            final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            registerError(nameIdentifier, Integer.valueOf(1));
        }


        public void visitTypeElement(PsiTypeElement typeElement) {
            super.visitTypeElement(typeElement);
            final PsiElement lastChild = typeElement.getLastChild();
            if (!(lastChild instanceof PsiTypeElement)) {
                return;
            }
            final PsiType type = typeElement.getType();
            if (!(type instanceof PsiWildcardType)) {
                return;
            }
            final PsiWildcardType wildcardType = (PsiWildcardType) type;
            final PsiType extendsBound = wildcardType.getExtendsBound();
            if (!TypeUtils.isJavaLangObject(extendsBound)) {
                return;
            }
            final PsiElement firstChild = typeElement.getFirstChild();
            if (firstChild == null) {
                return;
            }
            registerError(firstChild, Integer.valueOf(2));
        }
    }
}