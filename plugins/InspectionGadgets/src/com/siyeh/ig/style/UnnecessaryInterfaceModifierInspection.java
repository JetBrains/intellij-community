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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UnnecessaryInterfaceModifierInspection extends BaseInspection {

    private static final Set<String> INTERFACE_REDUNDANT_MODIFIERS =
            new HashSet<String>(Arrays.asList(PsiModifier.ABSTRACT));
    private static final Set<String> CLASS_REDUNDANT_MODIFIERS =
            new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC,
                    PsiModifier.STATIC));
    private static final Set<String> FIELD_REDUNDANT_MODIFIERS =
            new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC,
                    PsiModifier.STATIC, PsiModifier.FINAL));
    private static final Set<String> METHOD_REDUNDANT_MODIFIERS =
            new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC,
                    PsiModifier.ABSTRACT));

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiModifierList modifierList = (PsiModifierList)infos[0];
        assert modifierList != null;
        final PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiClass) {
            return InspectionGadgetsBundle.message(
                    "unnecessary.interface.modifier.problem.descriptor");
        } else if (parent instanceof PsiMethod) {
            if (modifierList.getChildren().length > 1) {
                return InspectionGadgetsBundle.message(
                        "unnecessary.interface.modifier.problem.descriptor1");
            } else {
                return InspectionGadgetsBundle.message(
                        "unnecessary.interface.modifier.problem.descriptor2");
            }
        } else {
            if (modifierList.getChildren().length > 1) {
                return InspectionGadgetsBundle.message(
                        "unnecessary.interface.modifier.problem.descriptor3");
            } else {
                return InspectionGadgetsBundle.message(
                        "unnecessary.interface.modifier.problem.descriptor4");
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryInterfaceModifierVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessaryInterfaceModifersFix(location);
    }

    private static class UnnecessaryInterfaceModifersFix
            extends InspectionGadgetsFix {

        private final String m_name;

        private UnnecessaryInterfaceModifersFix(PsiElement fieldModifiers) {
            super();
            m_name = InspectionGadgetsBundle.message(
                    "smth.unnecessary.remove.quickfix",
                    fieldModifiers.getText());
        }

        @NotNull
        public String getName() {
            return m_name;
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiModifierList modifierList;
            if (element instanceof PsiModifierList) {
                modifierList = (PsiModifierList)element;
            } else {
                modifierList = (PsiModifierList)element.getParent();
            }
            assert modifierList != null;
            modifierList.setModifierProperty(PsiModifier.STATIC, false);
            modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
            modifierList.setModifierProperty(PsiModifier.FINAL, false);
            final PsiElement parent = modifierList.getParent();
            assert parent != null;
            if (parent instanceof PsiClass) {
                if (ClassUtils.isInnerClass((PsiClass)parent))
                {        // do the inner classes
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                }
            } else {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
            }
        }
    }

    private static class UnnecessaryInterfaceModifierVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (aClass.isInterface()) {
                final PsiModifierList modifiers = aClass.getModifierList();
                checkForRedundantModifiers(modifiers,
                        INTERFACE_REDUNDANT_MODIFIERS);
            }
            final PsiClass parent = ClassUtils.getContainingClass(aClass);
            if (parent != null && parent.isInterface()) {
                final PsiModifierList modifiers = aClass.getModifierList();
                checkForRedundantModifiers(modifiers,
                        CLASS_REDUNDANT_MODIFIERS);
            }
        }

        public void visitField(@NotNull PsiField field) {
            // don't call super, to keep this from drilling in
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.isInterface()) {
                return;
            }
            final PsiModifierList modifiers = field.getModifierList();
            checkForRedundantModifiers(modifiers, FIELD_REDUNDANT_MODIFIERS);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            // don't call super, to keep this from drilling in
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (!aClass.isInterface()) {
                return;
            }
            final PsiModifierList modifiers = method.getModifierList();
            checkForRedundantModifiers(modifiers, METHOD_REDUNDANT_MODIFIERS);
        }

        public void checkForRedundantModifiers(PsiModifierList list,
                                               Set<String> modifiers) {
            if (list == null) {
                return;
            }
            final PsiElement[] children = list.getChildren();
            for (PsiElement child : children) {
                if (modifiers.contains(child.getText())) {
                    registerError(child, list);
                }
            }
        }
    }
}
