/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.*;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.Consumer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MethodRefCanBeReplacedWithLambdaInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodRefToLambdaVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1) {
      final Object info = infos[0];
      if (info instanceof FixFactory) {
        return ((FixFactory)info).create();
      }
    }
    return null;
  }

  private static class MethodRefToLambdaVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression methodReferenceExpression) {
      super.visitMethodReferenceExpression(methodReferenceExpression);
      final PsiType interfaceType = methodReferenceExpression.getFunctionalInterfaceType();
      if (interfaceType != null &&
          LambdaUtil.getFunctionalInterfaceMethod(interfaceType) != null &&
          methodReferenceExpression.resolve() != null) {
        registerError(methodReferenceExpression, getFixFactory(isWithSideEffects(methodReferenceExpression), isOnTheFly()));
      }
    }

    private static FixFactory getFixFactory(boolean withSideEffects, boolean onTheFly) {
      if (!withSideEffects) return MethodRefToLambdaFix::new;
      if (onTheFly || ApplicationManager.getApplication().isUnitTestMode()) return SideEffectsMethodRefToLambdaFix::new;
      return null;
    }

    private static boolean isWithSideEffects(PsiMethodReferenceExpression methodReferenceExpression) {
      final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        final List<PsiElement> sideEffects = new ArrayList<>();
        SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
        return !sideEffects.isEmpty();
      }
      return false;
    }
  }

  private static class MethodRefToLambdaFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiMethodReferenceExpression) {
        doFix(project, (PsiMethodReferenceExpression)element);
      }
    }

    protected void doFix(Project project, @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      LambdaRefactoringUtil.convertMethodReferenceToLambda(methodReferenceExpression, false, true);
    }
  }

  private static class SideEffectsMethodRefToLambdaFix extends MethodRefToLambdaFix {
    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    protected void doFix(Project project, @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      final AsyncResult<DataContext> contextFromFocus = DataManager.getInstance().getDataContextFromFocus();
      contextFromFocus.doWhenDone((Consumer<DataContext>)context -> {
        final Editor editor = CommonDataKeys.EDITOR.getData(context);
        if (editor != null) {
          CommandProcessor.getInstance()
            .executeCommand(project, () -> doFixAndRemoveSideEffects(editor, methodReferenceExpression), getFamilyName(), null);
        }
      });
    }

    private static void doFixAndRemoveSideEffects(@NotNull Editor editor, @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(methodReferenceExpression)) return;
      final PsiLambdaExpression lambdaExpression =
        WriteAction.compute(() -> LambdaRefactoringUtil.convertMethodReferenceToLambda(methodReferenceExpression, false, true));
      if (lambdaExpression != null) {
        LambdaRefactoringUtil.removeSideEffectsFromLambdaBody(editor, lambdaExpression);
      }
    }
  }

  private interface FixFactory extends Factory<InspectionGadgetsFix> {
  }
}
