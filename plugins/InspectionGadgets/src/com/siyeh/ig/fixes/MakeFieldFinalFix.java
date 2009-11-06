/*
 * Copyright 2007-2008 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.FinalUtils;
import com.siyeh.ig.psiutils.InitializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeFieldFinalFix extends InspectionGadgetsFix {

    private final String fieldName;

    private MakeFieldFinalFix(String fieldName) {
        this.fieldName = fieldName;
    }

    @Nullable
    public static InspectionGadgetsFix buildFix(PsiField field) {
        if (!FinalUtils.canFieldBeFinal(field)) {
            return null;
        }
        final String name = field.getName();
        return new MakeFieldFinalFix(name);
    }

    @NotNull
    public static InspectionGadgetsFix buildFixUnconditional(PsiField field) {
        return new MakeFieldFinalFix(field.getName());
    }

    @NotNull
    public String getName() {
        return InspectionGadgetsBundle.message("make.field.final.quickfix",
                fieldName);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
        final PsiElement element = descriptor.getPsiElement();
        final PsiField field;
        if (element instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)element;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiField)) {
                return;
            }
            field = (PsiField)target;
        } else {
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiField)) {
                return;
            }
            field = (PsiField)parent;
        }
        final PsiModifierList modifierList = field.getModifierList();
        if (modifierList == null) {
            return;
        }
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
    }

    private static boolean isInitializedInOneInitializer(
            @NotNull PsiField field){
        final PsiClass aClass = field.getContainingClass();
        if(aClass == null){
            return false;
        }
        boolean initializedInOneInitializer = false;
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for(final PsiClassInitializer initializer : initializers){
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            final PsiCodeBlock body = initializer.getBody();
            if(InitializationUtils.blockAssignsVariableOrFails(body, field)) {
                if (initializedInOneInitializer) {
                    return false;
                }
                initializedInOneInitializer = true;
            }
        }
        return true;
    }

    private static boolean isInitializedInOneStaticInitializer(
            @NotNull PsiField field){
        final PsiClass aClass = field.getContainingClass();
        if(aClass == null){
            return false;
        }
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        boolean initializedInOneStaticInitializer = false;
        for(final PsiClassInitializer initializer : initializers){
            if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            final PsiCodeBlock body = initializer.getBody();
            if(InitializationUtils.blockAssignsVariableOrFails(body, field)) {
                if (initializedInOneStaticInitializer) {
                    return false;
                }
                initializedInOneStaticInitializer = true;
            }
        }
        return initializedInOneStaticInitializer;
    }

    private static boolean isInitializedInConstructors(
            @NotNull PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final PsiMethod[] constructors = containingClass.getConstructors();
        if (constructors.length == 0) {
            return false;
        }
        for (PsiMethod constructor : constructors) {
            if (!InitializationUtils.methodAssignsVariableOrFails(
                    constructor, field)) {
                return false;
            }
        }
        return true;
    }
}