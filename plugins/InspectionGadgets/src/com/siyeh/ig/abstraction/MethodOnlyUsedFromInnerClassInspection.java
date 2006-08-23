/*
 * Copyright 2005-2006 Bas Leijdekkers
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
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class MethodOnlyUsedFromInnerClassInspection extends MethodInspection {

    /** @noinspection PublicField*/
    public boolean ignoreMethodsAccessedFromAnonymousClass = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "method.only.used.from.inner.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiNamedElement element = (PsiNamedElement)infos[0];
        final String name = element.getName();
        if (infos.length > 1) {
            if (Boolean.TRUE.equals(infos[1])) {
                return InspectionGadgetsBundle.message(
                        "method.only.used.from.inner.class.problem.descriptor.anonymous.extending",
                        name);
            }
            return InspectionGadgetsBundle.message(
                    "method.only.used.from.inner.class.problem.descriptor.anonymous.implementing",
                    name);
        }
        return InspectionGadgetsBundle.message(
                "method.only.used.from.inner.class.problem.descriptor", name);
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "method.only.used.from.inner.class.ignore.option"),
                this, "ignoreMethodsAccessedFromAnonymousClass");
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
            if (method.getNameIdentifier() == null) {
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
                    registerMethodError(method, superClass,
                            Boolean.valueOf(false));
                } else {
                    superClass = containingClass.getSuperClass();
                    if (superClass == null) {
                        return;
                    }
                    registerMethodError(method, superClass,
                            Boolean.valueOf(true));
                }
            } else {
                registerMethodError(method, containingClass);
            }
        }
    }

    private class MethodReferenceFinder implements Processor<PsiReference> {

        private final PsiClass methodClass;
        private final PsiMethod method;
        private boolean onlyAccessedFromInnerClass = false;

        private PsiClass cache = null;

        MethodReferenceFinder(@NotNull PsiMethod method) {
            this.method = method;
            methodClass = method.getContainingClass();
        }

        public boolean process(PsiReference reference) {
            final PsiElement element = reference.getElement();
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(element, PsiMethod.class);;
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
            final Query<PsiReference> query = ReferencesSearch.search(method);
            query.forEach(this);
            return onlyAccessedFromInnerClass;
        }

        public PsiClass getContainingClass() {
            return cache;
        }
    }
}