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
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ClassWithoutConstructorInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "class.without.constructor.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "class.without.constructor.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new ClassWithoutConstructorFix();
    }

    private static class ClassWithoutConstructorFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "class.without.constructor.create.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement classIdentifier = descriptor.getPsiElement();
            final PsiClass aClass = (PsiClass)classIdentifier.getParent();
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiMethod constructor = factory.createConstructor();
            final PsiModifierList modifierList = constructor.getModifierList();
            if (aClass == null) {
                return;
            }
            if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            } else if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
            } else if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
            } else if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
            }
            aClass.add(constructor);
            final CodeStyleManager styleManager =
                    psiManager.getCodeStyleManager();
            styleManager.reformat(constructor);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutConstructorVisitor();
    }

    private static class ClassWithoutConstructorVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isEnum() ||
                    aClass.isAnnotationType() || PsiUtil.isInJspFile(aClass)) {
                return;
            }
            if (aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass) {
                return;
            }
            final PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length > 0) {
                return;
            }
            registerClassError(aClass);
        }
    }
}