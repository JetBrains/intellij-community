/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

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
    return InspectionGadgetsBundle.message("static.inheritance.fix.family.name");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void doFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    doFix(project, descriptor, false);
  }

  private void doFix(final Project project, ProblemDescriptor descriptor, boolean inPreview) {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
    final PsiClass iface = (PsiClass)referenceElement.resolve();
    assert iface != null;
    final PsiField[] allFields = iface.getAllFields();

    final PsiClass implementingClass = ClassUtils.getContainingClass(referenceElement);
    assert implementingClass != null;
    final PsiFile file = implementingClass.getContainingFile();

    if (inPreview) {
      processUsages(allFields, implementingClass, project, true, iface, file);
    } else {
      ProgressManager.getInstance().run(new Task.Modal(project,
                                                       JavaAnalysisBundle.message("static.inheritrance.fix.replace.progress", iface.getName()), false) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          processUsages(allFields, implementingClass, project, false, iface, file);
        }
      });
    }
  }

  private void processUsages(PsiField[] allFields,
                         PsiClass implementingClass,
                         Project project,
                         boolean inPreview,
                         PsiClass iface,
                         PsiFile file) {
    for (final PsiField field : allFields) {
      SearchScope scope = ReadAction.compute(() -> implementingClass.getUseScope());
      if (inPreview) {
        scope = scope.intersectWith(new LocalSearchScope(file));
      }
      final Query<PsiReference> search = ReferencesSearch.search(field, scope, false);
      for (PsiReference reference : search) {
        if (!(reference instanceof PsiReferenceExpression)) {
          continue;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)reference;
        if (!myReplaceInWholeProject) {
          boolean isInheritor =
            ReadAction.compute(() -> {
              boolean isInheritor1 = false;
              PsiClass aClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
              while (aClass != null) {
                isInheritor1 = InheritanceUtil.isInheritorOrSelf(aClass, implementingClass, true);
                if (isInheritor1) break;
                aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
              }
              return isInheritor1;
            });
          if (!isInheritor) continue;
        }
        final Runnable runnable = () -> {
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
          final PsiReferenceExpression qualified = (PsiReferenceExpression)
            elementFactory.createExpressionFromText("xxx." + referenceExpression.getText(), referenceExpression);
          final PsiReferenceExpression newReference = (PsiReferenceExpression)referenceExpression.replace(qualified);
          final PsiReferenceExpression qualifier = (PsiReferenceExpression)newReference.getQualifierExpression();
          assert qualifier != null : DebugUtil.psiToString(newReference, true);
          final PsiClass containingClass = field.getContainingClass();
          qualifier.bindToElement(containingClass);
        };
        if (inPreview) {
          runnable.run();
        } else {
          WriteCommandAction.runWriteCommandAction(project, null, null, runnable,
                                                   ReadAction.compute(() -> referenceExpression.getContainingFile()));
        }
      }
    }
    final Runnable runnable = () -> {
      PsiClassType classType = JavaPsiFacade.getInstance(project).getElementFactory().createType(iface);
      IntentionAction fix = QuickFixFactory.getInstance().createExtendsListFix(implementingClass, classType, false);
      fix.invoke(project, null, file);
    };
    if (inPreview) {
      runnable.run();
    } else {
      WriteCommandAction.runWriteCommandAction(project, null, null, runnable, file);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    doFix(project, previewDescriptor, true);
    return IntentionPreviewInfo.DIFF;
  }
}
