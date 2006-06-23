/*
 * Copyright 2006 Dave Griffith
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

package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class DesignForExtensionInspection extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "design.for.extension.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "design.for.extension.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DesignForExtensionVisitor();
    }

    private static class DesignForExtensionVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if(method.isConstructor()) {
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE)||
                    method.hasModifierProperty(PsiModifier.FINAL) ||
                    method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass==null) {
                return;
            }
            if(containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if(containingClass.getName()==null) {
                return; //anonymous classes can't be overridden
            }
            final PsiCodeBlock body = method.getBody();
            if(body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if(statements.length!=0) {
                return;
            }
            registerMethodError(method);
        }
    }
}