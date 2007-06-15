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
package com.siyeh.ig.maturity;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class ClassWithoutToStringInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "class.without.tostring.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "class.without.tostring.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutToStringVisitor();
    }

    private static class ClassWithoutToStringVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            //don't call super, to prevent drilldown
            if (aClass.getNameIdentifier() == null) {
                return;
            }
            if (aClass.isInterface() || aClass.isAnnotationType() ||
                    aClass.isEnum()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter) {
                return;
            }
            if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                final String methodName = method.getName();
                final PsiParameterList parameterList = method.getParameterList();
                if(HardcodedMethodConstants.TO_STRING.equals(methodName) &&
                        parameterList.getParametersCount() == 0){
                    return;
                }
            }
            registerClassError(aClass);
        }
    }
}