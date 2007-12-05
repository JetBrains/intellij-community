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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.NormalizeDeclarationFix;
import org.jetbrains.annotations.NotNull;

public class MultipleDeclarationInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "multiple.declaration.display.name");
    }

    @NotNull
    public String getID() {
        return "MultipleVariablesInDeclaration";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "multiple.declaration.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new NormalizeDeclarationFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MultipleDeclarationVisitor();
    }

    private static class MultipleDeclarationVisitor
            extends BaseInspectionVisitor {

        @Override public void visitDeclarationStatement(
                PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            if (statement.getDeclaredElements().length <= 1) {
                return;
            }
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiForStatement) {
                final PsiForStatement forStatement = (PsiForStatement)parent;
                final PsiStatement initialization =
                        forStatement.getInitialization();
                if (statement.equals(initialization)) {
                    return;
                }
            }
            final PsiElement[] declaredVars = statement.getDeclaredElements();
            for (int i = 1; i < declaredVars.length; i++) {
                //skip the first one;
                final PsiLocalVariable var = (PsiLocalVariable)declaredVars[i];
                registerVariableError(var);
            }
        }

        @Override public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if (childrenContainTypeElement(field)) {
                return;
            }
            if (field instanceof PsiEnumConstant) {
                return;
            }
            registerFieldError(field);
        }

        public static boolean childrenContainTypeElement(PsiElement field) {
            final PsiElement[] children = field.getChildren();
            for (PsiElement aChildren : children) {
                if (aChildren instanceof PsiTypeElement) {
                    return true;
                }
            }
            return false;
        }
    }
}