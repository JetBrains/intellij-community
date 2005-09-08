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
package com.siyeh.ig.assignment;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionListStatement;
import com.intellij.psi.PsiExpressionStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class NestedAssignmentInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Nested assignment";
    }

    public String getGroupDisplayName() {
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested assignment '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedAssignmentVisitor();
    }

    private static class NestedAssignmentVisitor extends BaseInspectionVisitor {

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            final PsiElement parent = expression.getParent();
            if(parent == null) {
                return;
            }
            final PsiElement grandparent = parent.getParent();
            if (parent instanceof PsiExpressionStatement ||
                    grandparent instanceof PsiExpressionListStatement) {
                return;
            }
            registerError(expression);
        }
    }
}