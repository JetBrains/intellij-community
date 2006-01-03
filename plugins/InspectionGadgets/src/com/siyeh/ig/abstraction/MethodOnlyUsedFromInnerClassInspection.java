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
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nullable;

public class MethodOnlyUsedFromInnerClassInspection extends MethodInspection {

    String text = null;
    boolean anonymousClass = false;
    boolean anonymousExtends = false;

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
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
            final PsiManager manager = method.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final PsiClass containingClass = method.getContainingClass();
            final LocalSearchScope scope =
                    new LocalSearchScope(containingClass);
            final PsiReference[] references =
                    searchHelper.findReferences(method, scope, false);
            if (references.length == 0) {
                return;
            }
            final PsiReference firstReference = references[0];
            final PsiElement firstElement = firstReference.getElement();
            final PsiClass firstReferenceClass =
                    ClassUtils.getContainingClass(firstElement);
            if (firstReferenceClass == null ||
                !PsiTreeUtil.isAncestor(containingClass,
                                        firstReferenceClass, true)) {
                return;
            }
            for (int i = 1; i < references.length; i++) {
                final PsiReference reference = references[i];
                final PsiElement element = reference.getElement();
                final PsiClass referenceClass =
                        PsiTreeUtil.getParentOfType(element, PsiClass.class);
                if (!firstReferenceClass.equals(referenceClass)) {
                    return;
                }
            }
            if (firstReferenceClass instanceof PsiAnonymousClass) {
                final PsiClass[] interfaces =
                        firstReferenceClass.getInterfaces();
                final PsiClass superClass;
                if (interfaces.length == 1) {
                    superClass = interfaces[0];
                    anonymousExtends = false;
                    text = superClass.getName();
                } else {
                    superClass = firstReferenceClass.getSuperClass();
                    if (superClass == null) {
                        return;
                    }
                    anonymousExtends = true;
                    text = superClass.getName();
                }
                anonymousClass = true;
            } else {
                anonymousClass = false;
                text = firstReferenceClass.getName();
            }
            registerMethodError(method);
        }
    }
}