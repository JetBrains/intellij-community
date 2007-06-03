/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnStringObjectInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "synchronize.on.string.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "synchronize.on.string.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SynchronizeOnLockVisitor();
    }

    private static class SynchronizeOnLockVisitor
            extends BaseInspectionVisitor {

        public void visitSynchronizedStatement(
                @NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if (lockExpression == null) {
                return;
            }
            final PsiType type = lockExpression.getType();
            if (type == null || !type.equalsToText("java.lang.String")) {
                return;
            }
            registerError(lockExpression);
        }
    }
}