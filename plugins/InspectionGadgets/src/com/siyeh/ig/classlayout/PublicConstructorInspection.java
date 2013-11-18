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

package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
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
public class PublicConstructorInspection extends PublicConstructorInspectionBase {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceConstructorWithFactoryMethodFix();
  }

  private class ReplaceConstructorWithFactoryMethodFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("public.constructor.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class, PsiMethod.class);
      final AsyncResult<DataContext> context = DataManager.getInstance().getDataContextFromFocus();
      context.doWhenDone(new Consumer<DataContext>() {
        @Override
        public void consume(DataContext dataContext) {
          final JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
          final RefactoringActionHandler handler = factory.createReplaceConstructorWithFactoryHandler();
          handler.invoke(project, new PsiElement[]{element}, dataContext);
        }
      });
    }
  }
}
