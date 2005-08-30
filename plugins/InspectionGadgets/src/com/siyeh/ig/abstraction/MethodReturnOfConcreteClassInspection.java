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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MethodReturnOfConcreteClassInspection extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("method.return.concrete.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("method.return.concrete.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodReturnOfConcreteClassVisitor();
    }

    private static class MethodReturnOfConcreteClassVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor()) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            registerError(typeElement);
        }

    }

}
