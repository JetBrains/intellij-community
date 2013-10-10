/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 06-Jan-2006
 */
public class BooleanMethodIsAlwaysInvertedInspection extends BooleanMethodIsAlwaysInvertedInspectionBase {
  @Override
  public QuickFix getQuickFix(final String hint) {
    return new InvertMethodFix();
  }

  private static class InvertMethodFix implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return "Invert method";
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      assert psiMethod != null;
      final RefactoringActionHandler invertBooleanHandler = JavaRefactoringActionHandlerFactory.getInstance().createInvertBooleanHandler();
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          invertBooleanHandler.invoke(project, new PsiElement[]{psiMethod}, DataManager.getInstance().getDataContext());
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        runnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
      }
    }
  }
}
