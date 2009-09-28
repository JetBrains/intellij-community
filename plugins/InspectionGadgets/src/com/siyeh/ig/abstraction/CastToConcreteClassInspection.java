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
package com.siyeh.ig.abstraction;

import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class CastToConcreteClassInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "cast.to.concrete.class.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "cast.to.concrete.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CastToConcreteClassVisitor();
    }

    private static class CastToConcreteClassVisitor
            extends BaseInspectionVisitor {

        @Override public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiTypeElement typeElement = expression.getCastType();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            if (typeElement == null) {
                return;
            }
            registerError(typeElement);
        }
    }
}
