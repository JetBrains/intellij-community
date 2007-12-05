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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryContinueInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.continue.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.continue.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryContinueVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new DeleteUnnecessaryStatementFix("continue");
    }

    private static class UnnecessaryContinueVisitor
            extends BaseInspectionVisitor {

        @Override public void visitContinueStatement(
                @NotNull PsiContinueStatement statement) {
          if (PsiUtil.isInJspFile(statement.getContainingFile())) {
            return;
          }
            final PsiStatement continuedStatement =
                    statement.findContinuedStatement();
            PsiStatement body = null;
            if (continuedStatement instanceof PsiForeachStatement) {
                final PsiForeachStatement foreachStatement =
                        (PsiForeachStatement)continuedStatement;
                body = foreachStatement.getBody();
            } else if (continuedStatement instanceof PsiForStatement) {
                final PsiForStatement forStatement =
                        (PsiForStatement)continuedStatement;
                body = forStatement.getBody();
            } else if (continuedStatement instanceof PsiDoWhileStatement) {
                final PsiDoWhileStatement doWhileStatement =
                        (PsiDoWhileStatement)continuedStatement;
                body = doWhileStatement.getBody();
            } else if (continuedStatement instanceof PsiWhileStatement) {
                final PsiWhileStatement whileStatement =
                        (PsiWhileStatement)continuedStatement;
                body = whileStatement.getBody();
            }
            if (body == null) {
                return;
            }
            if (body instanceof PsiBlockStatement) {
                final PsiCodeBlock block =
                        ((PsiBlockStatement)body).getCodeBlock();
                if (ControlFlowUtils.blockCompletesWithStatement(block,
                        statement)) {
                    registerStatementError(statement);
                }
            } else if (ControlFlowUtils.statementCompletesWithStatement(body,
                    statement)) {
                registerStatementError(statement);
            }
        }
    }
}