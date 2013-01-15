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

package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PublicConstructorInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("public.constructor.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (((Boolean)infos[0]).booleanValue()) {
      return InspectionGadgetsBundle.message("public.default.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("public.constructor.problem.descriptor");
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

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

    @Override
    protected void doFix(final Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class, PsiMethod.class);
      final AsyncResult<DataContext> context = DataManager.getInstance().getDataContextFromFocus();
      context.doWhenDone(new AsyncResult.Handler<DataContext>() {
        @Override
        public void run(DataContext dataContext) {
          final JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
          final RefactoringActionHandler handler = factory.createReplaceConstructorWithFactoryHandler();
          handler.invoke(project, new PsiElement[]{element}, dataContext);
        }
      });
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorVisitor();
  }

  private static class PublicConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (SerializationUtils.isExternalizable(aClass)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          return;
        }
      }
      registerMethodError(method, Boolean.FALSE);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface() || aClass.isEnum()) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.PUBLIC) || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length > 0) {
        return;
      }
      if (SerializationUtils.isExternalizable(aClass)) {
        return;
      }
      registerClassError(aClass, Boolean.TRUE);
    }
  }
}
