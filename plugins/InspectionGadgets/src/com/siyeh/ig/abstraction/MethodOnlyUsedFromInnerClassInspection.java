/*
 * Copyright 2005 Bas Leijdekkers
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
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class MethodOnlyUsedFromInnerClassInspection extends MethodInspection {

    /** @noinspection PublicField*/
    public boolean ignoreMethodsAccessedFromAnonymousClass = false;

    String text = null;
    boolean anonymousClass = false;
    boolean anonymousExtends = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "method.only.used.from.inner.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "method.only.used.from.inner.class.ignore.option.name"),
                this, "ignoreMethodsAccessedFromAnonymousClass");
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        if (anonymousClass) {
          if (anonymousExtends) {
            return InspectionGadgetsBundle.message(
                    "method.only.used.from.inner.class.problem.descriptor.anonymous.extending",
                    text);
          }
          return InspectionGadgetsBundle.message(
                  "method.only.used.from.inner.class.problem.descriptor.anonymous.implementing",
                  text);
        }
        return InspectionGadgetsBundle.message(
                "method.only.used.from.inner.class.problem.descriptor", text);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodOnlyUsedFromNestedClassVisitor();
    }

    private class MethodOnlyUsedFromNestedClassVisitor
        extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (!method.hasModifierProperty(PsiModifier.PRIVATE) ||
                method.isConstructor()) {
                return;
            }
            final MethodReferenceFinder processor =
                    new MethodReferenceFinder(method);
            if (!processor.isOnlyAccessedFromInnerClass()) {
                return;
            }
            final PsiClass containingClass = processor.getContainingClass();
            if (containingClass instanceof PsiAnonymousClass) {
                final PsiClass[] interfaces =
                        containingClass.getInterfaces();
                final PsiClass superClass;
                if (interfaces.length == 1) {
                    superClass = interfaces[0];
                    anonymousExtends = false;
                    text = superClass.getName();
                } else {
                    superClass = containingClass.getSuperClass();
                    if (superClass == null) {
                        return;
                    }
                    anonymousExtends = true;
                    text = superClass.getName();
                }
                anonymousClass = true;
            } else {
                anonymousClass = false;
                text = containingClass.getName();
            }
            registerMethodError(method);
        }
    }

    private class MethodReferenceFinder
            implements PsiReferenceProcessor {

        private final PsiClass methodClass;
        private final PsiMethod method;
        private boolean onlyAccessedFromInnerClass = false;

        private PsiClass cache = null;

        MethodReferenceFinder(@NotNull PsiMethod method) {
            this.method = method;
            methodClass = method.getContainingClass();
        }

        public boolean execute(PsiReference reference) {
            final PsiElement element = reference.getElement();
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (method.equals(containingMethod)) {
                return true;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(element);
            if (containingClass == null) {
                onlyAccessedFromInnerClass = false;
                return false;
            }
            if (ignoreMethodsAccessedFromAnonymousClass &&
                    containingClass instanceof PsiAnonymousClass) {
                onlyAccessedFromInnerClass = false;
                return false;
            }
            if (cache != null) {
                if (!cache.equals(containingClass)) {
                    onlyAccessedFromInnerClass = false;
                    return false;
                }
            } else if (!PsiTreeUtil.isAncestor(methodClass, containingClass,
                    true)) {
                onlyAccessedFromInnerClass = false;
                return false;
            }
            onlyAccessedFromInnerClass = true;
            cache = containingClass;
            return true;
        }

        public boolean isOnlyAccessedFromInnerClass() {
            final PsiClass methodClass = method.getContainingClass();
            final LocalSearchScope scope =
                    new LocalSearchScope(methodClass);

            final PsiManager manager = method.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final ProgressManager progressManager =
                    ProgressManager.getInstance();
            progressManager.runProcess(new Runnable() {
                public void run() {
                    searchHelper.processReferences(
                            MethodReferenceFinder.this, method, scope, false);
                }
            }, null);
            return onlyAccessedFromInnerClass;
        }

        public PsiClass getContainingClass() {
            return cache;
        }
    }
}