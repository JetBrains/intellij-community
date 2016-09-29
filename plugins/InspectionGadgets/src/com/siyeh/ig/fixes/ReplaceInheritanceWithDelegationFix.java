/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ReplaceInheritanceWithDelegationFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message(
      "replace.inheritance.with.delegation.quickfix");
  }

  @Override
  public void doFix(@NotNull final Project project, ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiClass aClass = (PsiClass)nameElement.getParent();
    assert !(aClass instanceof PsiAnonymousClass);
    final JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
    final RefactoringActionHandler inheritanceToDelegationHandler = factory.createInheritanceToDelegationHandler();
    final DataManager dataManager = DataManager.getInstance();
    final DataContext dataContext = dataManager.getDataContext();
    inheritanceToDelegationHandler.invoke(project, new PsiElement[]{aClass}, dataContext);
  }

  @Override
  protected boolean prepareForWriting() {
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}