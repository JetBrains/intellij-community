/*
 * Copyright 2006-2007 Bas Leijdekkers
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

public class StaticMethodOnlyUsedInOneClassInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "static.method.only.used.in.one.class.display.name");
    }

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

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new StaticMethodOnlyUsedInOneClassFix();
    }

    private static class StaticMethodOnlyUsedInOneClassFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "static.method.only.used.in.one.class.quickfix");
        }

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
                            dataManager.getDataContext();
                    moveHandler.invoke(project, new PsiElement[]{method},
                            dataContext);
                }
            });
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticmethodOnlyUsedInOneClassVisitor();
    }

    private static class StaticmethodOnlyUsedInOneClassVisitor
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

        private PsiClass usageClass = null;

        public boolean process(PsiReference reference) {
            ProgressManager.checkCanceled();
            final PsiElement element = reference.getElement();
            final PsiClass usageClass =
                    ClassUtils.getContainingClass(element);
          synchronized (this) {
            if (this.usageClass != null &&
                    !this.usageClass.equals(usageClass)) {
                this.usageClass = null;
                return false;
            }
            this.usageClass = usageClass;
          }
          return true;
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
            final FindUsagesCostProcessor costProcessor =
                    new FindUsagesCostProcessor();
            searchHelper.processAllFilesWithWord(name, scope,
                    costProcessor, true);
            if (costProcessor.isCostTooHigh()) {
                return null;
            }
            progressManager.runProcess(new Runnable() {
                public void run() {
                    final Query<PsiReference> query =
                            MethodReferencesSearch.search(method);
                    query.forEach(UsageProcessor.this);
                }
            }, null);
          synchronized (this) {
            return usageClass;
          }
        }

        private static class FindUsagesCostProcessor
                implements Processor<PsiFile> {

            private final AtomicInteger counter = new AtomicInteger();
            private volatile boolean costTooHigh = false;

            public boolean process(PsiFile psiFile) {
              if (counter.incrementAndGet() >= 10) {
                costTooHigh = true;
                return false;
              }
                return true;
            }

            public boolean isCostTooHigh() {
                return costTooHigh;
            }
        }
    }
}
