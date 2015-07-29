/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.ui.UIUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
class StaticInheritanceFix extends InspectionGadgetsFix {
  private final boolean myReplaceInWholeProject;

  StaticInheritanceFix(boolean replaceInWholeProject) {
    myReplaceInWholeProject = replaceInWholeProject;
  }

  @Override
  @NotNull
  public String getName() {
    String scope =
      myReplaceInWholeProject ? InspectionGadgetsBundle.message("the.whole.project") : InspectionGadgetsBundle.message("this.class");
    return InspectionGadgetsBundle.message("static.inheritance.replace.quickfix", scope);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace inheritance with qualified reference";
  }

  @Override
  public void doFix(final Project project, final ProblemDescriptor descriptor) throws IncorrectOperationException {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        dodoFix(project, descriptor);
      }
    }, ModalityState.NON_MODAL, project.getDisposed());
  }

  private void dodoFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
    final PsiClass iface = (PsiClass)referenceElement.resolve();
    assert iface != null;
    final PsiField[] allFields = iface.getAllFields();

    final PsiClass implementingClass = ClassUtils.getContainingClass(referenceElement);
    final PsiManager manager = referenceElement.getManager();
    assert implementingClass != null;
    final PsiFile file = implementingClass.getContainingFile();

    ProgressManager.getInstance().run(new Task.Modal(project, "Replacing usages of " + iface.getName(), false) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (final PsiField field : allFields) {
          SearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
                      @Override
                      public SearchScope compute() {
                        return implementingClass.getUseScope();
                      }
                    });
          final Query<PsiReference> search = ReferencesSearch.search(field, scope, false);
          for (PsiReference reference : search) {
            if (!(reference instanceof PsiReferenceExpression)) {
              continue;
            }
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)reference;
            if (!myReplaceInWholeProject) {
              boolean isInheritor =
              ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
                @Override
                public Boolean compute() {
                  boolean isInheritor = false;
                  PsiClass aClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
                  while (aClass != null) {
                    isInheritor = InheritanceUtil.isInheritorOrSelf(aClass, implementingClass, true);
                    if (isInheritor) break;
                    aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
                  }
                  return isInheritor;
                }
              });
              if (!isInheritor) continue;
            }
            final Runnable runnable = new Runnable() {
              @Override
              public void run() {
                if (!FileModificationService.getInstance().preparePsiElementsForWrite(referenceExpression)) {
                  return;
                }
                final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
                final PsiReferenceExpression qualified =
                  (PsiReferenceExpression)elementFactory
                    .createExpressionFromText("xxx." + referenceExpression.getText(), referenceExpression);
                final PsiReferenceExpression newReference = (PsiReferenceExpression)referenceExpression.replace(qualified);
                final PsiReferenceExpression qualifier = (PsiReferenceExpression)newReference.getQualifierExpression();
                assert qualifier != null : DebugUtil.psiToString(newReference, false);
                final PsiClass containingClass = field.getContainingClass();
                qualifier.bindToElement(containingClass);
              }
            };
            invokeWriteAction(runnable, file);
          }
        }
        final Runnable runnable = new Runnable() {
          @Override
          public void run() {
            PsiClassType classType = JavaPsiFacade.getInstance(project).getElementFactory().createType(iface);
            IntentionAction fix = QuickFixFactory.getInstance().createExtendsListFix(implementingClass, classType, false);
            fix.invoke(project, null, file);
          }
        };
        invokeWriteAction(runnable, file);
      }
    });
  }

  private static void invokeWriteAction(final Runnable runnable, final PsiFile file) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        new WriteCommandAction(file.getProject(), file) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            runnable.run();
          }
        }.execute();
      }
    });
  }
}
