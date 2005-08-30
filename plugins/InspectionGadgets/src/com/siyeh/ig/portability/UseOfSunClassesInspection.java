/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.portability;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class UseOfSunClassesInspection extends VariableInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("use.sun.classes.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("use.sun.classes.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObsoleteCollectionVisitor();
    }

    private static class ObsoleteCollectionVisitor extends BaseInspectionVisitor {
        @NonNls private final String prefix = "sun.";
        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiType deepComponentType = type.getDeepComponentType();
            if (deepComponentType == null) {
                return;
            }
            if(!(deepComponentType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) deepComponentType;
            final String className = classType.getCanonicalText();

            if(className == null || !className.startsWith(prefix)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitNewExpression(@NotNull PsiNewExpression newExpression) {
            super.visitNewExpression(newExpression);
            final PsiType type = newExpression.getType();
            if (type == null) {
                return;
            }
            if(!(type instanceof PsiClassType))
            {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final String className = classType.getCanonicalText();
            if (className==null || !className.startsWith(prefix)) {
                return;
            }
            final PsiJavaCodeReferenceElement classNameElement = newExpression.getClassReference();
            registerError(classNameElement);
        }

    }

}
