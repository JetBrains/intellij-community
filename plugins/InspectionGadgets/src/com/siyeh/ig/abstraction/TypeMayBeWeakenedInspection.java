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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TypeMayBeWeakenedInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return "Type may be weakened";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final Collection<PsiClass> weakerClasses =
                (Collection<PsiClass>) infos[0];
        final StringBuilder builder = new StringBuilder();
        final Iterator<PsiClass> iterator = weakerClasses.iterator();
        if (iterator.hasNext()) {
            builder.append('\'');
            builder.append(iterator.next().getQualifiedName());
            builder.append('\'');
            while (iterator.hasNext()) {
                builder.append(", '");
                builder.append(iterator.next().getQualifiedName());
                builder.append('\'');
            }
        }
        return "Type of variable <code>#ref</code> may be weakened to " +
                builder.toString();
    }

    @Nullable
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final PsiLocalVariable variable =
                (PsiLocalVariable) location.getParent();
        final Collection<PsiClass> weakestClasses =
                TypeUtils.calculateWeakestClassesNecessary(variable);
        if (weakestClasses == null) {
            return null;
        }
        final List<InspectionGadgetsFix> fixes = new ArrayList();
        for (PsiClass weakestClass : weakestClasses) {
            fixes.add(new TypeMayBeWeakenedFix(weakestClass.getQualifiedName()));
        }
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {

        private final String fqClassName;

        TypeMayBeWeakenedFix(String fqClassName) {
            this.fqClassName = fqClassName;
        }

        @NotNull
        public String getName() {
            return "Weaken type to '" + fqClassName + '\'';
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
            final PsiManager manager = variable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiClassType type =
                    factory.createTypeByFQClassName(fqClassName,
                            element.getResolveScope());
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
            final Collection<PsiClass> weakestClasses =
                    TypeUtils.calculateWeakestClassesNecessary(variable);
            if (weakestClasses == null) {
                return;
            }
            registerVariableError(variable, weakestClasses);
        }
    }
}