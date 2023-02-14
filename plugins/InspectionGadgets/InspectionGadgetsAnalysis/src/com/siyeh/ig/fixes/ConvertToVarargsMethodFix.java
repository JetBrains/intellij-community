/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToVarargsMethodFix extends InspectionGadgetsFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.to.variable.arity.method.quickfix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiMethod method)) {
      return;
    }
    final Collection<PsiElement> writtenElements = new ArrayList<>();
    writtenElements.add(method);
    final Collection<PsiReferenceExpression> methodCalls = new ArrayList<>();
    for (final PsiReference reference : ReferencesSearch.search(method, method.getUseScope(), false)) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiReferenceExpression) {
        writtenElements.add(referenceElement);
        methodCalls.add((PsiReferenceExpression)referenceElement);
      }
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(writtenElements)) {
      return;
    }
    WriteAction.run(() -> {
      makeMethodVarargs(method);
      makeMethodCallsVarargs(methodCalls);
    });
  }

  private static void makeMethodVarargs(PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.isEmpty()) {
      return;
    }
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiParameter lastParameter = parameters[parameters.length - 1];
    lastParameter.normalizeDeclaration();
    final PsiType type = lastParameter.getType();
    if (!(type instanceof PsiArrayType arrayType)) {
      return;
    }
    final PsiType componentType = arrayType.getComponentType();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    final PsiType ellipsisType = new PsiEllipsisType(componentType, TypeAnnotationProvider.Static.create(type.getAnnotations()));
    final PsiTypeElement newTypeElement = factory.createTypeElement(ellipsisType);
    final PsiTypeElement typeElement = lastParameter.getTypeElement();
    if (typeElement != null) {
      typeElement.replace(newTypeElement);
    }
  }

  private static void makeMethodCallsVarargs(Collection<PsiReferenceExpression> referenceExpressions) {
    for (final PsiReferenceExpression referenceExpression : referenceExpressions) {
      final PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiMethodCallExpression methodCallExpression)) {
        continue;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        continue;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      if (!(lastArgument instanceof PsiNewExpression newExpression)) {
        continue;
      }
      final PsiArrayInitializerExpression arrayInitializerExpression = newExpression.getArrayInitializer();
      if (arrayInitializerExpression == null) {
        continue;
      }
      final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
      for (final PsiExpression initializer : initializers) {
        argumentList.add(initializer);
      }
      lastArgument.delete();
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    final PsiMethod method = ObjectUtils.tryCast(previewDescriptor.getPsiElement(), PsiMethod.class);
    if (method == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    makeMethodVarargs(method);
    List<PsiReferenceExpression> refsInFile = SyntaxTraverser.psiTraverser(method.getContainingFile()).filter(PsiReferenceExpression.class)
      .filter(ref -> ref.isReferenceTo(method)).toList();
    makeMethodCallsVarargs(refsInFile);
    return IntentionPreviewInfo.DIFF;
  }
}
