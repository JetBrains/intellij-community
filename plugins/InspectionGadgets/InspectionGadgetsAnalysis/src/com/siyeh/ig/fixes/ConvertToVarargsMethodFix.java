/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToVarargsMethodFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @NotNull
  @Override
  public String getName() {
    return InspectionGadgetsBundle.message("convert.to.variable.arity.method.quickfix");
  }

  @Override
  protected boolean prepareForWriting() {
    return false;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod)parent;
    final Collection<PsiElement> writtenElements = new ArrayList<>();
    final Collection<PsiReferenceExpression> methodCalls = new ArrayList<>();
    writtenElements.add(method);
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
    makeMethodVarargs(method);
    makeMethodCallsVarargs(methodCalls);
  }

  private static void makeMethodVarargs(PsiMethod method) {
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() == 0) {
      return;
    }
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiParameter lastParameter = parameters[parameters.length - 1];
    final PsiType type = lastParameter.getType();
    if (!(type instanceof PsiArrayType)) {
      return;
    }
    final PsiArrayType arrayType = (PsiArrayType)type;
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
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)referenceExpression.getParent();
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        continue;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      if (!(lastArgument instanceof PsiNewExpression)) {
        continue;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)lastArgument;
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
}
