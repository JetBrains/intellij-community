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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CatchGenericClassInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "catch.generic.class.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "catch.generic.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CatchGenericClassVisitor();
    }

    private static class CatchGenericClassVisitor
            extends BaseInspectionVisitor {

        @Override public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final Set<PsiType> exceptionsThrown =
                    ExceptionUtils.calculateExceptionsThrown(tryBlock);
            final PsiParameter[] parameters =
                    statement.getCatchBlockParameters();
            for (final PsiParameter parameter : parameters) {
                checkParameter(parameter, exceptionsThrown);
            }
        }

        private void checkParameter(PsiParameter parameter,
                                    Set<PsiType> exceptionsThrown) {
            final PsiType type = parameter.getType();
            if (!ExceptionUtils.isGenericExceptionClass(type)) {
                return;
            }
            if (exceptionsThrown.contains(type)) {
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            registerError(typeElement);
        }
    }
}