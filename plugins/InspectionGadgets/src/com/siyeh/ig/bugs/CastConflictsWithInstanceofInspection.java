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
package com.siyeh.ig.bugs;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;

public class CastConflictsWithInstanceofInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CastConflictsWithInstanceofVisitor();
    }

    private static class CastConflictsWithInstanceofVisitor
            extends BaseInspectionVisitor {

        @Override public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiType castType = expression.getType();
            final PsiExpression operand = expression.getOperand();
            if (!InstanceOfUtils.hasConflictingInstanceof(expression)) {
                return;
            }
            registerError(expression, castType, operand);
        }
    }
}