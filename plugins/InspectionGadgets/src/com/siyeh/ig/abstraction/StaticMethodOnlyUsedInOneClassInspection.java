/*
 * Copyright 2006-2010 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class StaticMethodOnlyUsedInOneClassInspection
        extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "static.method.only.used.in.one.class.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiNamedElement element = (PsiNamedElement)infos[0];
        final String name = element.getName();
        if (infos.length > 1) {
            if (Boolean.TRUE.equals(infos[1])) {
                return InspectionGadgetsBundle.message(
                        "static.method.only.used.in.one.class.problem.descriptor.anonymous.extending",
                        name);
            }
            return InspectionGadgetsBundle.message(
                    "static.method.only.used.in.one.class.problem.descriptor.anonymous.implementing",
                    name);
        }
        return InspectionGadgetsBundle.message(
                "static.method.only.used.in.one.class.problem.descriptor", name);
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiClass usageClass = (PsiClass)infos[0];
        return new StaticMethodOnlyUsedInOneClassFix(usageClass);
    }

    private static class StaticMethodOnlyUsedInOneClassFix
            extends InspectionGadgetsFix {

        private final PsiClass usageClass;

        public StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass) {
            this.usageClass = usageClass;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "static.method.only.used.in.one.class.quickfix");
        }

        @Override
        protected void doFix(@NotNull final Project project,
                             ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement location = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)location.getParent();
            final Application application = ApplicationManager.getApplication();
            application.invokeLater(new Runnable() {
                public void run() {
                    final RefactoringActionHandlerFactory factory =
                            RefactoringActionHandlerFactory.getInstance();
                    final RefactoringActionHandler moveHandler =
                            factory.createMoveHandler();
                    final DataManager dataManager = DataManager.getInstance();
                    final DataContext dataContext =
                            new DataContext() {

                                public Object getData(@NonNls String name) {
                                    if (LangDataKeys.TARGET_PSI_ELEMENT.is(name)) {
                                        return usageClass;
                                    }
                                    return dataManager.getDataContext().getData(name);
                                }
                            };
                    moveHandler.invoke(project,
                            new PsiElement[]{method}, dataContext);
                }
            });
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StaticMethodOnlyUsedInOneClassVisitor();
    }

    private static class StaticMethodOnlyUsedInOneClassVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final UsageProcessor usageProcessor = new UsageProcessor();
            final PsiClass usageClass = usageProcessor.getUsageClass(method);
            if (usageClass == null) {
                return;
            }
            if (usageClass.equals(method.getContainingClass())) {
                return;
            }
            if (usageClass instanceof PsiAnonymousClass) {
                final PsiClass[] interfaces =
                        usageClass.getInterfaces();
                final PsiClass superClass;
                if (interfaces.length == 1) {
                    superClass = interfaces[0];
                    registerMethodError(method, superClass, Boolean.FALSE);
                } else {
                    superClass = usageClass.getSuperClass();
                    if (superClass == null) {
                        return;
                    }
                    registerMethodError(method, superClass, Boolean.TRUE);
                }
            } else {
                registerMethodError(method, usageClass);
            }
        }
    }

    private static class UsageProcessor implements Processor<PsiReference> {

        private final AtomicReference<PsiClass> foundClass =
                new AtomicReference<PsiClass>();

        public boolean process(PsiReference reference) {
            ProgressManager.checkCanceled();
            final PsiElement element = reference.getElement();
            final PsiClass usageClass = ClassUtils.getContainingClass(element);
            if (usageClass == null) {
                return true;
            }
            if (foundClass.compareAndSet(null, usageClass)) {
                return true;
            }
            final PsiClass aClass = foundClass.get();
            final PsiManager manager = usageClass.getManager();
            return manager.areElementsEquivalent(aClass, usageClass);
        }

        /**
         * @return the class the specified method is used from, or null if it is
         * used from 0 or more than 1 other classes.
         */
        @Nullable
        public PsiClass getUsageClass(final PsiMethod method) {
            final ProgressManager progressManager =
                    ProgressManager.getInstance();
            final PsiManager manager = method.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final String name = method.getName();
            final GlobalSearchScope scope =
                    GlobalSearchScope.allScope(method.getProject());
            if (searchHelper.isCheapEnoughToSearch(name, scope, null) ==
                PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
                return null;
            }
            progressManager.runProcess(new Runnable() {
                public void run() {
                    final Query<PsiReference> query =
                            MethodReferencesSearch.search(method);
                    if (!query.forEach(UsageProcessor.this)) {
                      foundClass.set(null);
                    }
                }
            }, null);
            return foundClass.get();
        }
    }
}
