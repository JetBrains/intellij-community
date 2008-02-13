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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryReturnInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "UnnecessaryReturnStatement";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.return.display.name");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final boolean isConstructor = ((Boolean)infos[0]).booleanValue();
        if (isConstructor) {
            return InspectionGadgetsBundle.message(
                    "unnecessary.return.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "unnecessary.return.problem.descriptor1");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryReturnVisitor();
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new DeleteUnnecessaryStatementFix("return");
    }

    private static class UnnecessaryReturnVisitor
            extends BaseInspectionVisitor {

        @Override public void visitReturnStatement(
                @NotNull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
          if (PsiUtil.isInJspFile(statement.getContainingFile())) {
            return;
          }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            if (method == null) {
                return;
            }
            final Boolean isConstructor;
            if (method.isConstructor()) {
                isConstructor = Boolean.TRUE;
            } else {
                final PsiType returnType = method.getReturnType();
                if (!PsiType.VOID.equals(returnType)) {
                    return;
                }
                isConstructor = Boolean.FALSE;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.blockCompletesWithStatement(body, statement)) {
                registerStatementError(statement, isConstructor);
            }
        }
    }
}