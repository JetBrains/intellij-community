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
package com.siyeh.ig.classlayout;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class TypeParameterExtendsFinalClassInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "type.parameter.extends.final.class.display.name");
    }

    @Nls
    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiNamedElement namedElement = (PsiNamedElement)infos[0];
        final String name = namedElement.getName();
        return InspectionGadgetsBundle.message(
                "type.parameter.extends.final.class.problem.descriptor", name);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TypeParameterExtendsFinalClassVisitor();
    }

    private class TypeParameterExtendsFinalClassVisitor
            extends BaseInspectionVisitor {

        public void visitTypeParameter(PsiTypeParameter classParameter) {
            super.visitTypeParameter(classParameter);
            final PsiClassType[] extendsListTypes =
                    classParameter.getExtendsListTypes();
            if (extendsListTypes.length < 1) {
                return;
            }
            final PsiClassType extendsType = extendsListTypes[0];
            final PsiClass aClass = extendsType.resolve();
            if (aClass == null) {
                return;
            }
            if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiIdentifier nameIdentifier =
                    classParameter.getNameIdentifier();
            if (nameIdentifier != null) {
                registerError(nameIdentifier, aClass);
            }
        }
    }
}