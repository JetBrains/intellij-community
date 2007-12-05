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
package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SwitchStatementWithConfusingDeclarationInspection
        extends BaseInspection {

    @NotNull
    public String getID() {
        return "LocalVariableUsedAndDeclaredInDifferentSwitchBranches";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "switch.statement.with.confusing.declaration.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "switch.statement.with.confusing.declaration.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementWithConfusingDeclarationVisitor();
    }

    private static class SwitchStatementWithConfusingDeclarationVisitor
            extends BaseInspectionVisitor {

        @Override public void visitSwitchStatement(
                @NotNull PsiSwitchStatement statement) {
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final Set<PsiLocalVariable> variablesInPreviousBranches =
                    new HashSet<PsiLocalVariable>(10);
            final Set<PsiLocalVariable> variablesInCurrentBranch =
                    new HashSet<PsiLocalVariable>(10);
            final PsiStatement[] statements = body.getStatements();
            for (final PsiStatement child : statements) {
                if (child instanceof PsiDeclarationStatement) {
                    final PsiDeclarationStatement declaration =
                            (PsiDeclarationStatement)child;
                    final PsiElement[] declaredElements =
                            declaration.getDeclaredElements();
                    for (final PsiElement declaredElement : declaredElements) {
                        if (declaredElement instanceof PsiLocalVariable) {
                            final PsiLocalVariable localVar =
                                    (PsiLocalVariable)declaredElement;
                            variablesInCurrentBranch.add(localVar);
                        }
                    }
                }
                if (child instanceof PsiBreakStatement) {
                    variablesInPreviousBranches.addAll(
                            variablesInCurrentBranch);
                    variablesInCurrentBranch.clear();
                }
                final LocalVariableAccessVisitor visitor =
                        new LocalVariableAccessVisitor();
                child.accept(visitor);
                final Set<PsiElement> accessedVariables =
                        visitor.getAccessedVariables();
                for (Object accessedVariable : accessedVariables) {
                    final PsiLocalVariable localVar =
                            (PsiLocalVariable)accessedVariable;
                    if (variablesInPreviousBranches.contains(localVar)) {
                        variablesInPreviousBranches.remove(localVar);
                        registerVariableError(localVar);
                    }
                }
            }
        }
    }
}