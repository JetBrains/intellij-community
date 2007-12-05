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
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SwitchStatementsWithoutDefaultInspection
        extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_ignoreFullyCoveredEnums = true;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "switch.statements.without.default.display.name");
    }

    @NotNull
    public String getID() {
        return "SwitchStatementWithoutDefaultBranch";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "switch.statements.without.default.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "switch.statement.without.default.ignore.option"),
                this, "m_ignoreFullyCoveredEnums");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementsWithoutDefaultVisitor();
    }

    private class SwitchStatementsWithoutDefaultVisitor
            extends BaseInspectionVisitor {

        @Override public void visitSwitchStatement(
                @NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            if (switchStatementHasDefault(statement)) {
                return;
            }
            if (m_ignoreFullyCoveredEnums &&
                    switchStatementIsFullyCoveredEnum(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean switchStatementHasDefault(
                PsiSwitchStatement statement) {
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            for (final PsiStatement child : statements) {
                if (child instanceof PsiSwitchLabelStatement &&
                        ((PsiSwitchLabelStatement)child).isDefaultCase()) {
                    return true;
                }
            }
            return false;
        }

        private boolean switchStatementIsFullyCoveredEnum(
                PsiSwitchStatement statement) {
            final PsiExpression expression = statement.getExpression();
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return false;
            }
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass == null) {
                return false;
            }
            if (!aClass.isEnum()) {
                return false;
            }
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            int numCases = 0;
            for (final PsiStatement child : statements) {
                if (child instanceof PsiSwitchLabelStatement) {
                    numCases++;
                }
            }
            final PsiField[] fields = aClass.getFields();
            int numEnums = 0;
            for (final PsiField field : fields) {
                final PsiType fieldType = field.getType();
                if (fieldType.equals(type)) {
                    numEnums++;
                }
            }
            return numEnums == numCases;
        }
    }
}