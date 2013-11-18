/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class IntroduceVariableFix extends InspectionGadgetsFix {

  private final boolean myMayChangeSemantics;

  public IntroduceVariableFix(boolean mayChangeSemantics) {
    myMayChangeSemantics = mayChangeSemantics;
  }

  @NotNull
  @Override
  public String getName() {
    if (myMayChangeSemantics) {
      return InspectionGadgetsBundle.message("introduce.variable.may.change.semantics.quickfix");
    } else {
      return InspectionGadgetsBundle.message("introduce.variable.quickfix");
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("introduce.variable.quickfix");
  }

  @Nullable
  public PsiExpression getExpressionToExtract(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
  }

  @Override
  protected void doFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiExpression expression = getExpressionToExtract(descriptor.getPsiElement());
    if (expression == null) {
      return;
    }
    final RefactoringActionHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createIntroduceVariableHandler();
    final AsyncResult<DataContext> dataContextContainer = DataManager.getInstance().getDataContextFromFocus();
    dataContextContainer.doWhenDone(new Consumer<DataContext>() {
      @Override
      public void consume(DataContext dataContext) {
        handler.invoke(project, new PsiElement[]{expression}, dataContext);
      }
    });
  }
}
