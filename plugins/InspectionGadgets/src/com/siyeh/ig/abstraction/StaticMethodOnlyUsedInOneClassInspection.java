/*
 * Copyright 2006-2012 Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticMethodOnlyUsedInOneClassInspection extends StaticMethodOnlyUsedInOneClassInspectionBase {

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass usageClass = (PsiClass)infos[0];
    return new StaticMethodOnlyUsedInOneClassFix(usageClass);
  }

  private static class StaticMethodOnlyUsedInOneClassFix extends InspectionGadgetsFix {

    private final SmartPsiElementPointer<PsiClass> usageClass;

    public StaticMethodOnlyUsedInOneClassFix(PsiClass usageClass) {
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(usageClass.getProject());
      this.usageClass = pointerManager.createSmartPsiElementPointer(usageClass);
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("static.method.only.used.in.one.class.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(@NotNull final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement location = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)location.getParent();
      final RefactoringActionHandler moveHandler = RefactoringActionHandlerFactory.getInstance().createMoveHandler();
      final AsyncResult<DataContext> result = DataManager.getInstance().getDataContextFromFocus();
      result.doWhenDone(new Consumer<DataContext>() {
        @Override
        public void consume(final DataContext originalContext) {
          final DataContext dataContext = new DataContext() {
            @Override
            public Object getData(@NonNls String name) {
              if (LangDataKeys.TARGET_PSI_ELEMENT.is(name)) {
                return usageClass.getElement();
              }
              return originalContext.getData(name);
            }
          };
          moveHandler.invoke(project, new PsiElement[]{method}, dataContext);
        }
      });
    }
  }
}
