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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StaticInheritanceInspection extends ClassInspection {

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
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
                    PsiTreeUtil.getParentOfType(referenceElement,
                            PsiClass.class);
            final PsiManager manager = referenceElement.getManager();
            assert implementingClass != null;
            final SearchScope searchScope = implementingClass.getUseScope();
            final Map<PsiReferenceExpression, PsiField> refsToRebind =
                    new HashMap<PsiReferenceExpression, PsiField>();
            for (final PsiField field : allFields) {
                final Query<PsiReference> search =
                        ReferencesSearch.search(field, searchScope, false);
                for (PsiReference reference1 : search) {
                    if (!(reference1 instanceof PsiReferenceExpression)) {
                        continue;
                    }
                    final PsiReferenceExpression reference =
                            (PsiReferenceExpression)reference1;
                    if(isQuickFixOnReadOnlyFile(reference)){
                        continue;
                    }
                    refsToRebind.put(reference, field);
                }
            }
            deleteElement(referenceElement);
            for (PsiReferenceExpression reference : refsToRebind.keySet()) {
                final PsiField field = refsToRebind.get(reference);
                final PsiElementFactory elementFactory =
                        manager.getElementFactory();
                final PsiReferenceExpression qualified = (PsiReferenceExpression)
                        elementFactory.createExpressionFromText("xxx." +
                                reference.getText(), reference);
                final PsiReferenceExpression newReference =
                        (PsiReferenceExpression)reference.replace(qualified);
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)
                                newReference.getQualifierExpression();
                if (referenceExpression != null) {
                    referenceExpression.bindToElement(field.getContainingClass());
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticInheritanceVisitor();
    }

    private static class StaticInheritanceVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if (implementsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] refs =
                    implementsList.getReferenceElements();
            for (final PsiJavaCodeReferenceElement ref : refs) {
                final PsiClass iface = (PsiClass)ref.resolve();
                if (iface != null) {
                    if (interfaceContainsOnlyConstants(iface)) {
                        registerError(ref);
                    }
                }
            }
        }

        private static boolean interfaceContainsOnlyConstants(PsiClass iface) {
            if (iface.getAllFields().length == 0) {
                // ignore it, it's either a true interface or just a marker
                return false;
            }
            if (iface.getMethods().length != 0) {
                return false;
            }
            final PsiClass[] parentInterfaces = iface.getInterfaces();
            for (final PsiClass parentInterface : parentInterfaces) {
                if (!interfaceContainsOnlyConstants(parentInterface)) {
                    return false;
                }
            }
            return true;
        }
    }
}