/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class UtilityClassWithoutPrivateConstructorInspection
        extends BaseInspection {

    /** @noinspection PublicField for externalization*/
    public boolean ignoreClassesWithOnlyMain = false;

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "utility.class.without.private.constructor.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "utility.class.without.private.constructor.problem.descriptor");
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "utility.class.without.private.constructor.option"), this,
                "ignoreClassesWithOnlyMain");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiClass aClass = (PsiClass)infos[0];
        final PsiMethod constructor = getNullArgConstructor(aClass);
        if (constructor == null) {
            final Query<PsiReference> query =
                    ReferencesSearch.search(aClass, aClass.getUseScope());
            for (PsiReference reference : query) {
                if (reference == null) {
                    continue;
                }
                final PsiElement element = reference.getElement();
                final PsiElement parent = element.getParent();
                if (parent instanceof PsiNewExpression) {
                    return null;
                }
            }
            return new CreateEmptyPrivateConstructor();
        } else {
            final Query<PsiReference> query =
                    ReferencesSearch.search(constructor,
                            constructor.getUseScope());
            final PsiReference reference = query.findFirst();
            if (reference == null) {
                return new MakeConstructorPrivateFix();
            } else {
                return null;
            }
        }
    }

    private static class CreateEmptyPrivateConstructor
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "utility.class.without.private.constructor.create.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement classNameIdentifier = descriptor.getPsiElement();
            final PsiClass aClass = (PsiClass)classNameIdentifier.getParent();
            if (aClass == null) {
                return;
            }
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiElementFactory factory = psiFacade.getElementFactory();
            final PsiMethod constructor = factory.createConstructor();
            final PsiModifierList modifierList = constructor.getModifierList();
            modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            aClass.add(constructor);
            final CodeStyleManager styleManager =
                    CodeStyleManager.getInstance(project);
            styleManager.reformat(constructor);
        }
    }

    private static class MakeConstructorPrivateFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "utility.class.without.private.constructor.make.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement classNameIdentifier = descriptor.getPsiElement();
            final PsiClass aClass = (PsiClass)classNameIdentifier.getParent();
            if (aClass == null) {
                return;
            }
            final PsiMethod[] constructurs = aClass.getConstructors();
            for (final PsiMethod constructor : constructurs) {
                final PsiParameterList parameterList =
                        constructor.getParameterList();
                if (parameterList.getParametersCount() == 0) {
                    final PsiModifierList modifiers =
                            constructor.getModifierList();
                    modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
                    modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
                    modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
                }
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UtilityClassWithoutPrivateConstructorVisitor();
    }

    private class UtilityClassWithoutPrivateConstructorVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (!UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            if (ignoreClassesWithOnlyMain && hasOnlyMain(aClass)) {
                return;
            }
            if (hasPrivateConstructor(aClass)) {
                return;
            }
            final SearchScope scope =
                    GlobalSearchScope.projectScope(aClass.getProject());
            final Query<PsiClass> query =
                    ClassInheritorsSearch.search(aClass, scope, true, true);
            final PsiClass subclass = query.findFirst();
            if (subclass != null) {
                return;
            }
            registerClassError(aClass, aClass);
        }

        private boolean hasOnlyMain(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length == 0) {
                return false;
            }
            for (PsiMethod method : methods) {
                if (method.isConstructor()) {
                    continue;
                }
                if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                    return false;
                }
                if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                    continue;
                }
                if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return false;
                }
                final String name = method.getName();
                if (!name.equals(HardcodedMethodConstants.MAIN)) {
                    return false;
                }
                final PsiType returnType = method.getReturnType();
                if (!PsiType.VOID.equals(returnType)) {
                    return false;
                }
                final PsiParameterList parameterList =
                        method.getParameterList();
                if (parameterList.getParametersCount() != 1) {
                    return false;
                }
                final PsiParameter[] parameters = parameterList.getParameters();
                final PsiParameter parameter = parameters[0];
                final PsiType type = parameter.getType();
                if (!type.equalsToText("java.lang.String[]")) {
                    return false;
                }
            }
            return true;
        }

        boolean hasPrivateConstructor(PsiClass aClass) {
            final PsiMethod[] constructors = aClass.getConstructors();
            for (final PsiMethod constructor : constructors) {
                if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nullable
    static PsiMethod getNullArgConstructor(PsiClass aClass) {
        final PsiMethod[] constructors = aClass.getConstructors();
        for (final PsiMethod constructor : constructors) {
            final PsiParameterList params = constructor.getParameterList();
            if (params.getParametersCount() == 0) {
                return constructor;
            }
        }
        return null;
    }
}