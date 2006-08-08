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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PublicConstructorInNonPublicClassInspection
        extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "public.constructor.in.non.public.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiMethod method = (PsiMethod)infos[0];
        return InspectionGadgetsBundle.message(
                "public.constructor.in.non.public.class.problem.descriptor",
                method.getName());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PublicConstructorInNonPublicClassVisitor();
    }

    public InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final List<InspectionGadgetsFix> fixes = new ArrayList();
        final PsiModifierList modifierList =
                (PsiModifierList)location.getParent();
        final PsiMethod constructor = (PsiMethod)modifierList.getParent();
        final PsiClass aClass = constructor.getContainingClass();
        if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
            fixes.add(new SetConstructorModifierFix(PsiModifier.PROTECTED));
        } else if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
            fixes.add(new SetConstructorModifierFix(PsiModifier.PRIVATE));
        }
        fixes.add(new RemoveModifierFix(location));
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    private static class SetConstructorModifierFix
            extends InspectionGadgetsFix {

        private final String modifier;

        SetConstructorModifierFix(String modifier) {
            this.modifier = modifier;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "public.constructor.in.non.public.class.quickfix",
                    modifier
            );
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiModifierList modifierList =
                    (PsiModifierList) element.getParent();
            modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
            modifierList.setModifierProperty(modifier, true);
        }

    }

    private static class PublicConstructorInNonPublicClassVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.isConstructor()) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (SerializationUtils.isExternalizable(containingClass)) {
                final PsiParameterList parameterList =
                        method.getParameterList();
                final PsiParameter[] parameters = parameterList.getParameters();
                if (parameters.length == 0) {
                    return;
                }
            }
            registerModifierError(PsiModifier.PUBLIC, method, method);
        }
    }
}