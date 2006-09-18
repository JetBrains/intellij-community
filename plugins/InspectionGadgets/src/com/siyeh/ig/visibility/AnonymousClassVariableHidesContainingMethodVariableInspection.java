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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnonymousClassVariableHidesContainingMethodVariableInspection
        extends FieldInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "anonymous.class.variable.hides.containing.method.variable.display.name");
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final Object info = infos[0];
        if (info instanceof PsiParameter) {
            return InspectionGadgetsBundle.message(
                    "anonymous.class.parameter.hides.containing.method.variable.problem.descriptor");
        } else if (info instanceof PsiField) {
            return InspectionGadgetsBundle.message(
                    "anonymous.class.field.hides.containing.method.variable.problem.descriptor");
        }
        return InspectionGadgetsBundle.message(
                "anonymous.class.variable.hides.containing.method.variable.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RenameFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InnerClassVariableHidesOuterClassVariableVisitor();
    }

    private static class InnerClassVariableHidesOuterClassVariableVisitor
            extends BaseInspectionVisitor {

        public void visitAnonymousClass(PsiAnonymousClass aClass) {
            super.visitAnonymousClass(aClass);
            final VariableCollector collector = new VariableCollector();
            aClass.accept(collector);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(aClass, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            final PsiStatement[] statements = codeBlock.getStatements();
            final int offset = aClass.getTextOffset();
            for (PsiStatement statement : statements) {
                if (statement.getTextOffset() >= offset) {
                    break;
                }
                if (!(statement instanceof PsiDeclarationStatement)) {
                    continue;
                }
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement) statement;
                final PsiElement[] declaredElements =
                        declarationStatement.getDeclaredElements();
                for (PsiElement declaredElement : declaredElements) {
                    if (!(declaredElement instanceof PsiLocalVariable)) {
                        continue;
                    }
                    final PsiLocalVariable localVariable =
                            (PsiLocalVariable) declaredElement;
                    final String name = localVariable.getName();
                    final PsiVariable[] variables =
                            collector.getVariables(name);
                    for (PsiVariable variable : variables) {
                        registerVariableError(variable, variable);
                    }
                }
            }
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            final PsiParameterList parameterList =
                    containingMethod.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            for (PsiParameter parameter : parameters) {
                final String name = parameter.getName();
                final PsiVariable[] variables = collector.getVariables(name);
                for (PsiVariable variable : variables) {
                    registerVariableError(variable, variable);
                }
            }
        }
    }

    private static class VariableCollector extends PsiRecursiveElementVisitor {

        private static final PsiVariable[] EMPTY_VARIABLE_LIST = {};

        private Map<String, List<PsiVariable>> variableMap = new HashMap();

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            final String name = variable.getName();
            final List<PsiVariable> variableList = variableMap.get(name);
            if (variableList == null) {
                final List<PsiVariable> list = new ArrayList();
                list.add(variable);
                variableMap.put(name, list);
            } else {
                variableList.add(variable);
            }
        }

        public PsiVariable[] getVariables(String name) {
            final List<PsiVariable> variableList = variableMap.get(name);
            if (variableList == null) {
                return EMPTY_VARIABLE_LIST;
            } else {
                return variableList.toArray(
                        new PsiVariable[variableList.size()]);
            }
        }
    }
}