/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("extract.method.quickfix");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiExpression expression =
      (PsiExpression)descriptor.getPsiElement();
    final JavaRefactoringActionHandlerFactory factory =
      JavaRefactoringActionHandlerFactory.getInstance();
    final RefactoringActionHandler extractHandler =
      factory.createExtractMethodHandler();
    final DataManager dataManager = DataManager.getInstance();
    final DataContext dataContext = dataManager.getDataContext();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        extractHandler.invoke(project,
                              new PsiElement[]{expression}, dataContext);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }
}