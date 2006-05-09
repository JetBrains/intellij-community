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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

public class RedundantFieldInitializationInspection extends FieldInspection {

    @NonNls private static final Set<String> s_defaultValues =
            new HashSet<String>(10);

    static {
        s_defaultValues.add("null");
        s_defaultValues.add("0");
        s_defaultValues.add("false");
        s_defaultValues.add("0.0");
        s_defaultValues.add("0.0F");
        s_defaultValues.add("0.0f");
        s_defaultValues.add("0L");
        s_defaultValues.add("0l");
        s_defaultValues.add("0x0");
        s_defaultValues.add("0X0");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RedundantFieldInitializationVisitor();
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "redundant.field.initialization.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RedundantFieldInitializationFix();
    }

    private static class RedundantFieldInitializationFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "redundant.field.initialization.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression)descriptor.getPsiElement();
            PsiElement prevSibling = expression.getPrevSibling();
            PsiElement assignment = null;
            do {
                assert prevSibling != null;
                final PsiElement newPrevSibling = prevSibling.getPrevSibling();
                deleteElement(prevSibling);
                final String text = prevSibling.getText();
                if ("=".equals(text)) {
                    assignment = prevSibling;
                }
                prevSibling = newPrevSibling;
            } while (assignment == null);
            deleteElement(expression);
        }
    }

    private static class RedundantFieldInitializationVisitor
            extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if (!field.hasInitializer()) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiExpression initializer = field.getInitializer();
            if (initializer == null) {
                return;
            }
            final String text = initializer.getText();
            if (!s_defaultValues.contains(text)) {
                return;
            }
            final PsiType type = field.getType();
            if (!(type instanceof PsiPrimitiveType) &&
                    !text.equals(PsiKeyword.NULL)) {
                return;
            }
            registerError(initializer);
        }
    }
}