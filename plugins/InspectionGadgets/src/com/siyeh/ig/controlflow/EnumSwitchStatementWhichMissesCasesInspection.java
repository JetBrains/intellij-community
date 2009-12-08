/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EnumSwitchStatementWhichMissesCasesInspection
        extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "enum.switch.statement.which.misses.cases.display.name");
    }

    /** @noinspection PublicField*/
    public boolean ignoreSwitchStatementsWithDefault = false;

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiSwitchStatement switchStatement =
                (PsiSwitchStatement)infos[0];
        assert switchStatement != null;
        final PsiExpression switchStatementExpression =
                switchStatement.getExpression();
        assert switchStatementExpression != null;
        final PsiType switchStatementType =
                switchStatementExpression.getType();
        assert switchStatementType != null;
        final String switchStatementTypeText =
                switchStatementType.getPresentableText();
        return InspectionGadgetsBundle.message(
                "enum.switch.statement.which.misses.cases.problem.descriptor",
                switchStatementTypeText);
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "enum.switch.statement.which.misses.cases.option"),
                this, "ignoreSwitchStatementsWithDefault");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new EnumSwitchStatementWhichMissesCasesVisitor();
    }

    private class EnumSwitchStatementWhichMissesCasesVisitor
            extends BaseInspectionVisitor {

        @Override public void visitSwitchStatement(
                @NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            if (!switchStatementMissingCases(statement)) {
                return;
            }
            registerStatementError(statement, statement);
        }

        private boolean switchStatementMissingCases(
                PsiSwitchStatement statement) {
            final PsiExpression expression = statement.getExpression();
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
            if (type == null || !(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType)type;
            final PsiClass aClass = classType.resolve();
            if (aClass == null || !aClass.isEnum()) {
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
                    final PsiSwitchLabelStatement switchLabelStatement =
                            (PsiSwitchLabelStatement)child;
                    if (!switchLabelStatement.isDefaultCase()) {
                        numCases++;
                    } else if (ignoreSwitchStatementsWithDefault) {
                        return false;
                    }
                }
            }
            final PsiField[] fields = aClass.getFields();
            int numEnums = 0;
            for (final PsiField field : fields) {
                if (!(field instanceof PsiEnumConstant)) {
                    continue;
                }
                numEnums++;
            }
            return numEnums != numCases;
        }
    }
}
