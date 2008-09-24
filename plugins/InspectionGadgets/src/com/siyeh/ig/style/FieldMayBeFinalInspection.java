/*
 * Copyright 2008 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class FieldMayBeFinalInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "field.may.be.final.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "field.may.be.final.problem.descriptor");
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return MakeFieldFinalFix.buildFixUnconditional((PsiField)infos[0]);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new FieldMayBeFinalVisitor();
    }

    private static class FieldMayBeFinalVisitor extends BaseInspectionVisitor {

        @Override
        public void visitField(PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final ImplicitUsageProvider[] implicitUsageProviders =
                    Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
            for(ImplicitUsageProvider provider: implicitUsageProviders){
                if(provider.isImplicitWrite(field)){
                    return;
                }
            }
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                if (!staticFieldMayBeFinal(field)) {
                    return;
                }
            } else {
                if (!fieldMayBeFinal(field)) {
                    return;
                }
            }
            registerVariableError(field, field);
        }

        private static boolean fieldMayBeFinal(PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final PsiExpression intializer = field.getInitializer();
            final PsiClassInitializer[] classInitializers =
                    aClass.getInitializers();
            boolean assignedInInitializer = intializer != null;
            boolean isInitialized = assignedInInitializer;
            for (PsiClassInitializer classInitializer : classInitializers) {
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                if (VariableAccessUtils.variableIsAssigned(field,
                        classInitializer, false)) {
                    if (assignedInInitializer) {
                        return false;
                    } else if (InitializationUtils.
                            classInitializerAssignsVariableOrFails(
                                    classInitializer,  field)){
                        isInitialized = true;
                    }
                    assignedInInitializer = true;
                }
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (PsiMethod method : methods) {
                if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                if (method.isConstructor() && !assignedInInitializer) {
                    if (!VariableAccessUtils.variableIsAssigned(field, method,
                            false)) {
                        return false;
                    } else if (InitializationUtils.methodAssignsVariableOrFails(
                            method, field)){
                        isInitialized = true;
                    }
                    continue;
                }
                if (VariableAccessUtils.variableIsAssigned(field, method,
                        false)) {
                    return false;
                }
            }
            if (!isInitialized) {
                return false;
            }
            final PsiElement[] children = aClass.getChildren();
            final ClassVisitor visitor = new ClassVisitor(field);
            for (PsiElement child : children) {
                child.accept(visitor);
                if (visitor.isVariableAssignedInClass()) {
                    return false;
                }
            }
            PsiClass containingClass = aClass.getContainingClass();
            final AssigmentVisitor assignmentVisitor =
                    new AssigmentVisitor(field);
            while (containingClass != null) {
                containingClass.accept(assignmentVisitor);
                if (assignmentVisitor.isVariableAssigned()) {
                    return false;
                }
                containingClass = containingClass.getContainingClass();
            }
            return true;
        }

        private static boolean staticFieldMayBeFinal(PsiField field) {
            final PsiExpression initializer = field.getInitializer();
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final PsiClassInitializer[] classInitializers =
                    aClass.getInitializers();
            boolean assignedInInitializer = initializer != null;
            for (PsiClassInitializer classInitializer : classInitializers) {
                if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                    if (VariableAccessUtils.variableIsAssigned(field,
                            classInitializer, false)) {
                        if (assignedInInitializer) {
                            return false;
                        } else if (InitializationUtils.
                                classInitializerAssignsVariableOrFails(
                                        classInitializer, field)) {
                            assignedInInitializer = true;
                        }
                    }
                } else if (VariableAccessUtils.variableIsAssigned(field,
                        classInitializer,  false)) {
                    return false;
                }
            }
            if (!assignedInInitializer) {
                return false;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (PsiMethod method : methods) {
                if (VariableAccessUtils.variableIsAssigned(field, method,
                        false)) {
                    return false;
                }
            }
            final PsiElement[] children = aClass.getChildren();
            final ClassVisitor visitor = new ClassVisitor(field);
            for (PsiElement child : children) {
                child.accept(visitor);
                if (visitor.isVariableAssignedInClass()) {
                    return false;
                }
            }
            PsiClass containingClass = aClass.getContainingClass();
            final AssigmentVisitor assignmentVisitor =
                    new AssigmentVisitor(field);
            while (containingClass != null) {
                containingClass.accept(assignmentVisitor);
                if (assignmentVisitor.isVariableAssigned()) {
                    return false;
                }
                containingClass = containingClass.getContainingClass();
            }
            return true;
        }

        private static class ClassVisitor extends JavaRecursiveElementVisitor {

            private final PsiVariable variable;
            private boolean variableAssignedInClass = false;

            ClassVisitor(PsiVariable variable) {
                this.variable = variable;
            }

            @Override
            public void visitClass(PsiClass aClass) {
                if (variableAssignedInClass) {
                    return;
                }
                super.visitClass(aClass);
                if (VariableAccessUtils.variableIsAssigned(variable, aClass)) {
                    variableAssignedInClass = true;
                }
            }

            @Override
            public void visitElement(PsiElement element) {
                if (variableAssignedInClass) {
                    return;
                }
                super.visitElement(element);
            }

            public boolean isVariableAssignedInClass() {
                return variableAssignedInClass;
            }
        }

        private static class AssigmentVisitor
                extends JavaRecursiveElementVisitor {

            private final PsiVariable variable;
            private boolean variableAssigned = false;

            AssigmentVisitor(PsiVariable variable) {
                this.variable = variable;
            }

            @Override
            public void visitMethod(PsiMethod method) {
                if (variableAssigned) {
                    return;
                }
                super.visitMethod(method);
                if (VariableAccessUtils.variableIsAssigned(variable, method)) {
                    variableAssigned = true;
                }
            }

            @Override
            public void visitClassInitializer(PsiClassInitializer initializer) {
                if (variableAssigned) {
                    return;
                }
                super.visitClassInitializer(initializer);
                if (VariableAccessUtils.variableIsAssigned(variable,
                        initializer)) {
                    variableAssigned = true;
                }
            }

            public boolean isVariableAssigned() {
                return variableAssigned;
            }
        }
    }
}
