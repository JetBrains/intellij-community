// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandlerBase;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RefactoringInspectionGadgetsFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PublicConstructorInspection extends BaseInspection {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceConstructorWithFactoryMethodFix();
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

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorVisitor();
  }

  private static class ReplaceConstructorWithFactoryMethodFix extends RefactoringInspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("public.constructor.quickfix");
    }

    @NotNull
    @Override
    public RefactoringActionHandler getHandler() {
      return JavaRefactoringActionHandlerFactory.getInstance().createReplaceConstructorWithFactoryHandler();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      final var handler = (ReplaceConstructorWithFactoryHandlerBase)getHandler();
      final PsiElement element = getElementToRefactor(previewDescriptor.getPsiElement());
      if (element instanceof PsiMethod method) {
        handler.invokeForPreview(project, method);
      }
      else if (element instanceof PsiClass aClass) {
        handler.invokeForPreview(project, aClass);
      } else {
        return IntentionPreviewInfo.EMPTY;
      }
      return IntentionPreviewInfo.DIFF;
    }
  }

  private static class PublicConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.isRecord()) {
        return;
      }
      if (SerializationUtils.isExternalizable(aClass)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty()) {
          return;
        }
      }
      registerMethodError(method, Boolean.FALSE);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface() || aClass.isEnum() || aClass.isRecord()) {
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
