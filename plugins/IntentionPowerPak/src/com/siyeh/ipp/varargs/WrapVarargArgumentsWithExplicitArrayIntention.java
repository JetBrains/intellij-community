/*
 * Copyright 2007-2017 Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class WrapVarargArgumentsWithExplicitArrayIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new VarargArgumentsPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiCall call = PsiTreeUtil.getParentOfType(element, PsiCall.class);
    if (call == null) {
      return;
    }
    final PsiMethod method = call.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final int parametersCount = parameterList.getParametersCount();
    final PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) {
      return;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    final StringBuilder newExpressionText = new StringBuilder("new ");
    final PsiParameter[] parameters = parameterList.getParameters();
    final int varargParameterIndex = parametersCount - 1;
    final PsiType componentType = PsiTypesUtil.getParameterType(parameters, varargParameterIndex, true);
    final JavaResolveResult resolveResult = call.resolveMethodGenerics();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiType substitutedType = substitutor.substitute(componentType);
    if (substitutedType instanceof PsiCapturedWildcardType) {
      final PsiCapturedWildcardType capturedWildcardType = (PsiCapturedWildcardType)substitutedType;
      newExpressionText.append(capturedWildcardType.getLowerBound().getCanonicalText());
    } else {
      newExpressionText.append(substitutedType.getCanonicalText());
    }
    newExpressionText.append("[]{");
    if (arguments.length > varargParameterIndex) {
      final PsiExpression argument1 = arguments[varargParameterIndex];
      argument1.delete();
      newExpressionText.append(argument1.getText());
      for (int i = parametersCount; i < arguments.length; i++) {
        final PsiExpression argument = arguments[i];
        newExpressionText.append(',').append(argument.getText());
        argument.delete();
      }
    }
    newExpressionText.append("}");
    final Project project = element.getProject();
    final PsiExpression newExpression =
      JavaPsiFacade.getElementFactory(project).createExpressionFromText(newExpressionText.toString(), element);
    CodeStyleManager.getInstance(project).reformat(argumentList.add(newExpression));
  }
}