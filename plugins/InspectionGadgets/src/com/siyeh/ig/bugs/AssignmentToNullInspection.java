/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class AssignmentToNullInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("assignment.to.null.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "assignment.to.null.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToNullVisitor();
    }

    private static class AssignmentToNullVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(
                @NotNull PsiLiteralExpression value) {
            super.visitLiteralExpression(value);
            final String text = value.getText();
            if (!PsiKeyword.NULL.equals(text)) {
              return;
            }
            PsiElement parent = value.getParent();
            while (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiTypeCastExpression) {
                parent = parent.getParent();
            }
            if (!(parent instanceof PsiAssignmentExpression)) {
                return;
            }
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)parent;
            final PsiExpression lhs = assignmentExpression.getLExpression();
            if (lhs instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)lhs;
                final PsiElement element = referenceExpression.resolve();
                if (element instanceof PsiVariable) {
                    final PsiVariable variable = (PsiVariable)element;
                    if (AnnotationUtil.isNullable(variable)) {
                        return;
                    }
                }
            }
            registerError(lhs);
        }
    }
}