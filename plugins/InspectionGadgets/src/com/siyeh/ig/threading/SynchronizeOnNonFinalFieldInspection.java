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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.InitializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SynchronizeOnNonFinalFieldInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "synchronize.on.non.final.field.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "synchronize.on.non.final.field.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) location;
        final PsiField field = (PsiField) referenceExpression.resolve();
        if (field == null) {
            return null;
        }
        final boolean hasInitializer = field.hasInitializer();
        final boolean initializedInInitializer =
                isInitializedInInitializer(field);
        final boolean initializedInConstructors =
                isInitializedInConstructors(field);
        if (hasInitializer) {
            if (initializedInInitializer) {
                return null;
            }
            if (initializedInConstructors) {
                return null;
            }
        } else if (initializedInInitializer) {
            if (initializedInConstructors) {
                return null;
            }
        } else if (!initializedInConstructors) {
            return null;
        }
        final Query<PsiReference> query = ReferencesSearch.search(field);
        for (PsiReference reference : query) {
            final PsiElement element = reference.getElement();
            if (!(element instanceof PsiExpression)) {
                continue;
            }
            final PsiExpression expression = (PsiExpression) element;
            if (!PsiUtil.isOnAssignmentLeftHand(expression)) {
                continue;
            }
            final PsiMethod method = PsiTreeUtil.getParentOfType(
                    expression, PsiMethod.class);
            if (method != null && !method.isConstructor()) {
                return null;
            }
        }
        final String name = field.getName();
        return new MakeFieldFinalFix(name);
    }

    private static boolean isInitializedInInitializer(@NotNull PsiField field){
        final PsiClass aClass = field.getContainingClass();
        if(aClass == null){
            return false;
        }
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for(final PsiClassInitializer initializer : initializers){
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            final PsiCodeBlock body = initializer.getBody();
            if(InitializationUtils.blockAssignsVariableOrFails(body, field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInitializedInConstructors(
            @NotNull PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final PsiMethod[] constructors = containingClass.getConstructors();
        for (PsiMethod constructor : constructors) {
            if (!InitializationUtils.methodAssignsVariableOrFails(
                    constructor, field)) {
                return false;
            }
        }
        return true;
    }

    private static class MakeFieldFinalFix extends InspectionGadgetsFix {

        private final String name;

        MakeFieldFinalFix(String name) {
            this.name = name;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("make.field.final.quickfix",
                    name);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) element;
            final PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return;
            }
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SynchronizeOnNonFinalFieldVisitor();
    }

    private static class SynchronizeOnNonFinalFieldVisitor
            extends BaseInspectionVisitor {

        public void visitSynchronizedStatement(
                @NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if (!(lockExpression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReference reference = lockExpression.getReference();
            if (reference == null) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField)element;
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerError(lockExpression);
        }
    }
}