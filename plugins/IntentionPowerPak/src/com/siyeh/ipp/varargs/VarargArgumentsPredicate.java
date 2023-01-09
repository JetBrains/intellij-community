/*
 * Copyright 2007-2018 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class VarargArgumentsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof PsiExpressionList)) {
      return false;
    }
    final PsiExpressionList argumentList = (PsiExpressionList)element;
    final PsiElement parent = argumentList.getParent();
    if (!(parent instanceof PsiCall)) {
      return false;
    }
    final PsiCall call = (PsiCall)parent;
    final JavaResolveResult resolveResult = call.resolveMethodGenerics();
    if (!resolveResult.isValidResult() || !(resolveResult instanceof MethodCandidateInfo candidateInfo) ||
        candidateInfo.getApplicabilityLevel() != MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
      return false;
    }
    final PsiMethod method = candidateInfo.getElement();
    if (!method.isVarArgs()) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiType lastParameterType = PsiTypesUtil.getParameterType(parameters, parameters.length - 1, true);
    final PsiType substitutedType = substitutor.substitute(lastParameterType);
    if (substitutedType instanceof PsiCapturedWildcardType) {
      final PsiCapturedWildcardType capturedWildcardType = (PsiCapturedWildcardType)substitutedType;
      if (!capturedWildcardType.getWildcard().isSuper()) {
        // red code
        return false;
      }
    }

    if (arguments.length != parameters.length) {
      return true;
    }
    final PsiExpression lastExpression = arguments[arguments.length - 1];
    if (ExpressionUtils.isNullLiteral(lastExpression)) {
      // a single null argument is not wrapped in an array
      // on a vararg method call, but just passed as a null value
      return false;
    }
    final PsiType lastArgumentType = lastExpression.getType();
    if (!(lastArgumentType instanceof PsiArrayType)) {
      return true;
    }
    final PsiArrayType arrayType = (PsiArrayType)lastArgumentType;
    final PsiType type = arrayType.getComponentType();
    return !substitutedType.isAssignableFrom(type);
  }
}