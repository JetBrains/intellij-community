/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameParameterFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class ParameterNameDiffersFromOverriddenParameterInspection
        extends MethodInspection {

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreSingleCharacterNames = true;

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "parameter.name.differs.from.overridden.parameter.ignore.option"),
                this,
                "m_ignoreSingleCharacterNames");
    }

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
            if (parameters != null) {
                final String superParameterName = parameters[index].getName();
                if (!superParameterName.equals(parameterName)) {
                    return new RenameParameterFix(superParameterName);
                }
            }
        }
        return null;
    }

    public String buildErrorString(PsiElement location) {
        final PsiParameter parameter = (PsiParameter)location.getParent();
        assert parameter != null;
        final String parameterName = parameter.getName();
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
        assert method != null;
        final PsiMethod[] superMethods = method.findSuperMethods();
        final PsiParameterList methodParamList = method.getParameterList();
        final int index = methodParamList.getParameterIndex(parameter);
        for (final PsiMethod superMethod : superMethods) {
            final PsiParameterList parameterList =
                    superMethod.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters != null) {
                final String superParameterName =
                        parameters[index].getName();
                if (!superParameterName.equals(parameterName)) {
                    return InspectionGadgetsBundle.message(
                            "parameter.name.differs.from.overridden.parameter.problem.descriptor", superParameterName);
                }
            }
        }
        return "";// this can't happen
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ParameterNameDiffersFromOverriddenParameterVisitor();
    }

    private class ParameterNameDiffersFromOverriddenParameterVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null || parameters.length == 0) {
                return;
            }
            final PsiMethod[] superMethods =
                    method.findSuperMethods();
            if (superMethods.length == 0) {
                return;
            }
            for (int i = 0; i < parameters.length; i++) {
                checkParameter(parameters[i], i, superMethods);
            }
        }

        private void checkParameter(PsiParameter parameter, int index,
                                    PsiMethod[] superMethods) {
            final String parameterName = parameter.getName();
            for (final PsiMethod superMethod : superMethods) {
                final PsiParameterList parameterList =
                        superMethod.getParameterList();
                final PsiParameter[] parameters = parameterList.getParameters();
                if (parameters != null) {
                    final String superParameterName =
                            parameters[index].getName();
                    if (!superParameterName.equals(parameterName)) {
                        if (!m_ignoreSingleCharacterNames ||
                            superParameterName.length() != 1) {
                            registerVariableError(parameter);
                            return;
                        }
                    }
                }
            }
        }
    }
}