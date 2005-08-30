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
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class DeclareCollectionAsInterfaceInspection extends VariableInspection {

    public String getID(){
        return "CollectionDeclaredAsConcreteClass";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("collection.declared.by.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String type = location.getText();
        final String interfaceName = CollectionUtils.getInterfaceForClass(type);
        return InspectionGadgetsBundle.message("collection.declarated.by.class.problem.descriptor", interfaceName);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DeclareCollectionAsInterfaceVisitor();
    }

    private static class DeclareCollectionAsInterfaceVisitor extends BaseInspectionVisitor {

        public void visitVariable(@NotNull PsiVariable variable) {
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            final PsiType type = method.getReturnType();
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            registerError(typeElement);
        }

    }

}
