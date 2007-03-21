/*
 * Copyright 2006-2007 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeMayBeWeakenedInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return "Type may be weakened";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiClass aClass = (PsiClass) infos[0];
        return "Type of variable <code>#ref</code> may be weakened to '" +
                aClass.getQualifiedName() + "'";
    }


    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new TypeMayBeWeakenedFix();
    }

    private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Weaken type";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiVariable variable =
                    (PsiVariable)element.getParent();
            final PsiTypeElement typeElement =
                    variable.getTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiClassType type =
                    TypeUtils.calculateWeakestTypeNecessary(variable);
            if (type == null) {
                return;
            }
            final PsiManager manager = variable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiTypeElement newTypeElement =
                    factory.createTypeElement(type);
            typeElement.replace(newTypeElement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TypeMayBeWeakenedVisitor();
    }
    
    private static class TypeMayBeWeakenedVisitor
            extends BaseInspectionVisitor {

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiClassType weakestType =
                    TypeUtils.calculateWeakestTypeNecessary(variable);
            if (weakestType == null) {
                return;
            }
            final PsiType variableType = variable.getType();
            final String weakestTypeCanonicalText =
                    weakestType.getInternalCanonicalText();
            final String variableTypeCanonicalText =
                    variableType.getInternalCanonicalText();
            if (weakestTypeCanonicalText.equals(variableTypeCanonicalText)) {
                return;
            }
            final PsiClass weakestClass = weakestType.resolve();
            if (weakestClass == null) {
                return;
            }
            registerVariableError(variable, weakestClass);
        }

    }
}