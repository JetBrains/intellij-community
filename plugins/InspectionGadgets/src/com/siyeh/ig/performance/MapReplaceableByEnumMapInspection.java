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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class MapReplaceableByEnumMapInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "map.replaceable.by.enum.map.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "map.replaceable.by.enum.map.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SetReplaceableByEnumSetVisitor();
    }

    private static class SetReplaceableByEnumSetVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)type;
            if (!classType.hasParameters()) {
                return;
            }
            final PsiType[] typeArguments = classType.getParameters();
            if (typeArguments.length != 2) {
                return;
            }
            final PsiType argumentType = typeArguments[0];
            if (!(argumentType instanceof PsiClassType)) {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype(expression,
		            "java.util.Map")) {
                return;
            }
            if (TypeUtils.expressionHasTypeOrSubtype(expression,
		            "java.util.EnumMap")) {
	            return;
            }
            final PsiClassType argumentClassType = (PsiClassType)argumentType;
            final PsiClass argumentClass = argumentClassType.resolve();
            if (argumentClass == null) {
                return;
            }
            if (!argumentClass.isEnum()) {
                return;
            }
            registerNewExpressionError(expression);
        }
    }
}
