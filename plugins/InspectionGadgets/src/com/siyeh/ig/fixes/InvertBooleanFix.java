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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class InvertBooleanFix extends InspectionGadgetsFix {

  private final String myName;

  public InvertBooleanFix(String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("invert.quickfix.family.name");
  }

  @Override
  protected void doFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiNameIdentifierOwner owner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiVariable.class, PsiMethod.class);
    if (owner == null) {
      return;
    }
    final RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createInvertBooleanHandler();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        handler.invoke(project, new PsiElement[]{owner}, null);
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
