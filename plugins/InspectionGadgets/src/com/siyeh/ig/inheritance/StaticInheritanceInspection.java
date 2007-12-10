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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StaticInheritanceInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "static.inheritance.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "static.inheritance.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new StaticInheritanceFix();
    }

    private static class StaticInheritanceFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "static.inheritance.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
            final PsiClass iface = (PsiClass)referenceElement.resolve();
            assert iface != null;
            final PsiField[] allFields = iface.getAllFields();

            final PsiClass implementingClass =
                    ClassUtils.getContainingClass(referenceElement);
            final PsiManager manager = referenceElement.getManager();
            assert implementingClass != null;
            final SearchScope searchScope = implementingClass.getUseScope();
            final Map<PsiReferenceExpression, PsiField> refsToRebind =
                    new HashMap<PsiReferenceExpression, PsiField>();
            for (final PsiField field : allFields) {
                final Query<PsiReference> search =
                        ReferencesSearch.search(field, searchScope, false);
                for (PsiReference reference : search) {
                    if (!(reference instanceof PsiReferenceExpression)) {
                        continue;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)reference;
                    if(isQuickFixOnReadOnlyFile(referenceExpression)){
                        continue;
                    }
                    refsToRebind.put(referenceExpression, field);
                }
            }
            deleteElement(referenceElement);
            for (PsiReferenceExpression reference : refsToRebind.keySet()) {
                final PsiField field = refsToRebind.get(reference);
              final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
                final PsiReferenceExpression qualified = (PsiReferenceExpression)
                        elementFactory.createExpressionFromText("xxx." +
                                reference.getText(), reference);
                final PsiReferenceExpression newReference =
                        (PsiReferenceExpression)reference.replace(qualified);
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)
                                newReference.getQualifierExpression();
                if (referenceExpression != null) {
                    final PsiClass containingClass = field.getContainingClass();
                    referenceExpression.bindToElement(containingClass);
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticInheritanceVisitor();
    }

    private static class StaticInheritanceVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if (implementsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] references =
                    implementsList.getReferenceElements();
            for (final PsiJavaCodeReferenceElement reference : references) {
                final PsiClass iface = (PsiClass)reference.resolve();
                if (iface != null) {
                    if (interfaceContainsOnlyConstants(iface, new HashSet())) {
                        registerError(reference);
                    }
                }
            }
        }

        private static boolean interfaceContainsOnlyConstants(
                PsiClass iface, Set<PsiClass> visitedIntefaces) {
            if (!visitedIntefaces.add(iface)) {
                return true;
            }
            if (iface.getAllFields().length == 0) {
                // ignore it, it's either a true interface or just a marker
                return false;
            }
            if (iface.getMethods().length != 0) {
                return false;
            }
            final PsiClass[] parentInterfaces = iface.getInterfaces();
            for (final PsiClass parentInterface : parentInterfaces) {
                if (!interfaceContainsOnlyConstants(parentInterface,
                        visitedIntefaces)) {
                    return false;
                }
            }
            return true;
        }
    }
}