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
package com.siyeh.ipp.types;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InferLambdaParameterTypeIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#" + InferLambdaParameterTypeIntention.class.getName());
  private String myInferredTypesText;

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new LambdaParametersPredicate();
  }

  @NotNull
  @Override
  public String getText() {
    return "Expand lambda to " + myInferredTypesText + " -> {...}";
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
    LOG.assertTrue(lambdaExpression != null);
    final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
    final String buf = getInferredTypes(functionalInterfaceType, lambdaExpression, true);
    final Project project = element.getProject();
    final PsiMethod methodFromText = JavaPsiFacade.getElementFactory(project).createMethodFromText("void foo" + buf, element);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(lambdaExpression.getParameterList().replace(methodFromText.getParameterList()));
  }

  @Nullable
  private static String getInferredTypes(PsiType functionalInterfaceType, final PsiLambdaExpression lambdaExpression, boolean useFQN) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final StringBuilder buf = new StringBuilder();
    buf.append("(");
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    LOG.assertTrue(interfaceMethod != null);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] lambdaParameters = lambdaExpression.getParameterList().getParameters();
    if (parameters.length != lambdaParameters.length) return null;
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType psiType = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult).substitute(parameter.getType());
      if (!PsiTypesUtil.isDenotableType(psiType)) return null;
      if (psiType != null) {
        buf.append(useFQN ? psiType.getCanonicalText() : psiType.getPresentableText()).append(" ").append(lambdaParameters[i].getName());
      }
      else {
        buf.append(lambdaParameters[i].getName());
      }
      if (i < parameters.length - 1) {
        buf.append(", ");
      }
    }
    buf.append(")");
    return buf.toString();
  }


  private class LambdaParametersPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
        if (parameters.length == 0) return false;
        for (PsiParameter parameter : parameters) {
          if (parameter.getTypeElement() != null) {
            return false;
          }
        }
        if (PsiTreeUtil.isAncestor(lambdaExpression.getParameterList(), element, false)) {
          final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
          if (functionalInterfaceType != null && LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) != null && LambdaUtil.isLambdaFullyInferred(lambdaExpression, functionalInterfaceType)) {
            myInferredTypesText = getInferredTypes(functionalInterfaceType, lambdaExpression, false);
            return myInferredTypesText != null;
          }
        }
      }
      return false;
    }
  }
}
