/*
 * Copyright 2009 Bas Leijdekkers
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

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ListenerMayUseAdapterInspection extends BaseInspection {

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return "Class implementing listener may extend adapter instead";
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiClass adapterClass = (PsiClass) infos[0];
        final String adapterName = adapterClass.getName();
        return "Class implementing <code>#ref</code> may extend '" +
                adapterName + '\'';
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiClass adapterClass = (PsiClass) infos[0];
        return new ListenerMayUseAdapterFix(adapterClass);
    }

    private static class ListenerMayUseAdapterFix extends InspectionGadgetsFix {

        private final PsiClass adapterClass;

        ListenerMayUseAdapterFix(@NotNull PsiClass adapterClass) {
            this.adapterClass = adapterClass;
        }

        @NotNull
        public String getName() {
            return "Replace with 'extends " + adapterClass.getName() + '\'';
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiClass aClass = PsiTreeUtil.getParentOfType(element,
                    PsiClass.class);
            final PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            element.delete();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiElementFactory elementFactory =
                    psiFacade.getElementFactory();
            final PsiReferenceExpression referenceExpression =
                    elementFactory.createReferenceExpression(adapterClass);
            extendsList.add(referenceExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ListenerMayUseAdapterVisitor();
    }

    private static class ListenerMayUseAdapterVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitClass(PsiClass aClass) {
            final PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            final PsiReference[] extendsReferences =
                    extendsList.getReferences();
            if (extendsReferences.length > 0) {
                return;
            }
            final PsiReferenceList referenceList = aClass.getImplementsList();
            if (referenceList == null) {
                return;
            }
            final PsiReference[] implementsReferences =
                    referenceList.getReferences();
            for (PsiReference implementsReference : implementsReferences) {
                checkReference(aClass, implementsReference);
            }
        }

        private void checkReference(PsiClass aClass,
                                    PsiReference implementsReference) {
            final PsiElement target = implementsReference.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            final PsiClass implementsClass = (PsiClass) target;
            final String className = implementsClass.getQualifiedName();
            if (className == null || !className.endsWith("Listener")) {
                return;
            }
            final String adapterName = className.substring(0,
                    className.length() - 8) + "Adapter";
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(
                    aClass.getProject());
            final GlobalSearchScope scope =
                    implementsClass.getResolveScope();
            final PsiClass adapterClass = psiFacade.findClass(adapterName,
                    scope);
            if (adapterClass == null) {
                return;
            }
            registerError(implementsReference.getElement(), adapterClass);
        }
    }
}
