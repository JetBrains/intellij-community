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
package com.siyeh.ig.naming;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameParameterFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ParameterNameDiffersFromOverriddenParameterInspection
        extends BaseInspection {

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreSingleCharacterNames = false;

    /** @noinspection PublicField*/
    public boolean m_ignoreOverridesOfLibraryMethods = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "parameter.name.differs.from.overridden.parameter.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "parameter.name.differs.from.overridden.parameter.problem.descriptor",
                infos[0]);
    }

    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.name.differs.from.overridden.parameter.ignore.character.option"),
                "m_ignoreSingleCharacterNames");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "parameter.name.differs.from.overridden.parameter.ignore.library.option"),
                "m_ignoreOverridesOfLibraryMethods");
        return optionsPanel;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiParameter parameter = (PsiParameter)location.getParent();
        if (parameter == null) {
            return null;
        }
        final String parameterName = parameter.getName();
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
        if (method == null) {
            return null;
        }
        final PsiMethod[] superMethods =
                method.findSuperMethods();
        final PsiParameterList methodParamList = method.getParameterList();
        final int index = methodParamList.getParameterIndex(parameter);
        for (final PsiMethod superMethod : superMethods) {
            final PsiParameterList parameterList =
                    superMethod.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final String superParameterName = parameters[index].getName();
            assert superParameterName != null;
            if (!superParameterName.equals(parameterName)) {
                return new RenameParameterFix(superParameterName);
            }
        }
        return null;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ParameterNameDiffersFromOverriddenParameterVisitor();
    }

    private class ParameterNameDiffersFromOverriddenParameterVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 0) {
                return;
            }
            final Query<MethodSignatureBackedByPsiMethod> query =
                    SuperMethodsSearch.search(
                            method, method.getContainingClass(), true, false);
            final MethodSignatureBackedByPsiMethod methodSignature =
                    query.findFirst();
            if (methodSignature == null) {
                return;
            }
            final PsiMethod superMethod = methodSignature.getMethod();
            final PsiParameter[] parameters = parameterList.getParameters();
            checkParameters(superMethod, parameters);
        }

        private void checkParameters(PsiMethod superMethod,
                                     PsiParameter[] parameters) {
            if (m_ignoreOverridesOfLibraryMethods) {
                final PsiClass containingClass =
                        superMethod.getContainingClass();
                if (containingClass != null &&
                        LibraryUtil.classIsInLibrary(containingClass)) {
                    return;
                }
            }
            final PsiParameterList superParameterList =
                    superMethod.getParameterList();
            final PsiParameter[] superParameters =
                    superParameterList.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                final PsiParameter parameter = parameters[i];
                final String parameterName = parameter.getName();
                final String superParameterName = superParameters[i].getName();
                if (superParameterName == null) {
                    continue;
                }
                if (superParameterName.equals(parameterName)) {
                    continue;
                }
                if (m_ignoreSingleCharacterNames &&
                        superParameterName.length() == 1) {
                    continue;
                }
                registerVariableError(parameter, superParameterName);
            }
        }
    }
}