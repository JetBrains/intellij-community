/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.varargs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class MakeMethodVarargsIntention extends Intention {

  private static final Logger LOG = Logger.getInstance("#" + MakeMethodVarargsIntention.class.getName());

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new MakeMethodVarargsPredicate();
  }

  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    makeMethodVarargs(element);
    makeMethodCallsVarargs(element);
  }

  private static void makeMethodVarargs(PsiElement element)
    throws IncorrectOperationException {
    final PsiParameterList parameterList = (PsiParameterList)element;
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiParameter lastParameter = parameters[parameters.length - 1];
    final PsiType type = lastParameter.getType();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    final PsiTypeElement typeElement = lastParameter.getTypeElement();
    LOG.assertTrue(typeElement != null);
    final PsiType ellipsisType = new PsiEllipsisType(((PsiArrayType)type).getComponentType(), TypeAnnotationProvider.Static.create(type.getAnnotations()));
    typeElement.replace(factory.createTypeElement(ellipsisType));
  }

  private static void makeMethodCallsVarargs(PsiElement element)
    throws IncorrectOperationException {
    final PsiMethod method = (PsiMethod)element.getParent();
    final Query<PsiReference> query =
      ReferencesSearch.search(method, method.getUseScope(), false);
    for (PsiReference reference : query) {
      final PsiElement referenceElement = reference.getElement();
      if (!(referenceElement instanceof PsiReferenceExpression)) {
        continue;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)referenceElement;
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)referenceExpression.getParent();
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        continue;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      if (!(lastArgument instanceof PsiNewExpression)) {
        continue;
      }
      final PsiNewExpression newExpression =
        (PsiNewExpression)lastArgument;
      final PsiArrayInitializerExpression arrayInitializerExpression =
        newExpression.getArrayInitializer();
      if (arrayInitializerExpression == null) {
        continue;
      }
      final PsiExpression[] initializers =
        arrayInitializerExpression.getInitializers();
      for (PsiExpression initializer : initializers) {
        argumentList.add(initializer);
      }
      lastArgument.delete();
    }
  }
}
