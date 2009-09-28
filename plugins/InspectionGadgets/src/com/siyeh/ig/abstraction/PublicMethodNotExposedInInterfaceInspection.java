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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public class PublicMethodNotExposedInInterfaceInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "public.method.not.in.interface.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "public.method.not.in.interface.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PublicMethodNotExposedInInterface();
    }

    private static class PublicMethodNotExposedInInterface
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor()) {
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.isInterface() ||
                containingClass.isAnnotationType()) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (exposedInInterface(method)) {
                return;
            }
            if(TestUtils.isJUnitTestMethod(method)) {
                return;
            }
            registerMethodError(method);
        }

        private static boolean exposedInInterface(PsiMethod method) {
          final PsiMethod[] superMethods = method.findSuperMethods();
            for(final PsiMethod superMethod : superMethods) {
                final PsiClass superClass = superMethod.getContainingClass();
                if(superClass.isInterface()) {
                    return true;
                }
                final String superclassName = superClass.getQualifiedName();
                if("java.lang.Object".equals(superclassName)) {
                    return true;
                }
                if(exposedInInterface(superMethod)) {
                    return true;
                }
            }
            return false;
        }
    }
}