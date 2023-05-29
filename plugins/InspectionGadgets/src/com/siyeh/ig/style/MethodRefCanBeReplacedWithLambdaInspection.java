// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodRefCanBeReplacedWithLambdaInspection extends BaseInspection {

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
    final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)infos[0];
    final boolean onTheFly = (Boolean)infos[1];
    if (LambdaRefactoringUtil.canConvertToLambdaWithoutSideEffects(methodReferenceExpression)) {
      return new MethodRefToLambdaFix();
    }
    else if (onTheFly) {
      return new SideEffectsMethodRefToLambdaFix();
    }
    return null;
  }

  private static class MethodRefToLambdaVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      super.visitMethodReferenceExpression(methodReferenceExpression);
      if (LambdaRefactoringUtil.canConvertToLambda(methodReferenceExpression)) {
        registerError(methodReferenceExpression, methodReferenceExpression, isOnTheFly());
      }
    }
  }

  private static class MethodRefToLambdaFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return ApplicationManager.getApplication().isUnitTestMode() ? (InspectionGadgetsBundle
                                                                       .message("side.effects.method.ref.to.lambda.fix.family.name",
                                                                                super.getFamilyName())) : super.getFamilyName();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    protected void doFix(Project project, @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
      DataManager.getInstance()
                 .getDataContextFromFocusAsync()
                 .onSuccess(context -> {
                   final Editor editor = CommonDataKeys.EDITOR.getData(context);
                   if (editor != null) {
                     CommandProcessor.getInstance()
                                     .executeCommand(project, () -> doFixAndRemoveSideEffects(editor, methodReferenceExpression),
                                                     getFamilyName(), null);
                   }
                 });
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      PsiMethodReferenceExpression methodRef = ObjectUtils.tryCast(previewDescriptor.getPsiElement(), PsiMethodReferenceExpression.class);
      if (methodRef == null) {
        return IntentionPreviewInfo.EMPTY;
      }
      LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, false, true);
      return IntentionPreviewInfo.DIFF;
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
}
