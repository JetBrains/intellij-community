/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Bas Leijdekkers
 */
abstract class CollectionReplaceableByEnumCollectionVisitor extends BaseInspectionVisitor {

  @Override
  public final void visitNewExpression(@NotNull PsiNewExpression expression) {
    super.visitNewExpression(expression);
    final PsiType type = expression.getType();
    if (!(type instanceof PsiClassType)) {
      return;
    }
    PsiClassType classType = (PsiClassType)type;
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
    if (!(expectedType instanceof PsiClassType)) {
      return;
    }
    if (!classType.hasParameters()) {
      classType = (PsiClassType)expectedType;
    }
    final PsiType[] typeArguments = classType.getParameters();
    if (typeArguments.length == 0) {
      return;
    }
    final PsiType argumentType = typeArguments[0];
    if (!(argumentType instanceof PsiClassType)) {
      return;
    }
    if (!TypeUtils.expressionHasTypeOrSubtype(expression, getBaseCollectionName())) {
      return;
    }
    if (TypeUtils.expressionHasTypeOrSubtype(expression, getReplacementCollectionName()) ||
        TypeUtils.expressionHasTypeOrSubtype(expression, getUnreplaceableCollectionNames())) {
      return;
    }
    final PsiClassType argumentClassType = (PsiClassType)argumentType;
    final PsiClass argumentClass = argumentClassType.resolve();
    if (argumentClass == null || !argumentClass.isEnum()) {
      return;
    }
    final PsiClass aClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (argumentClass.equals(aClass)) {
      final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
      if (member != null && !member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 0 && TypeUtils.expressionHasTypeOrSubtype(arguments[0], "java.util.Comparator")) {
        return;
      }
    }
    PsiClassType replacementCollectionType = TypeUtils.getType(getReplacementCollectionName(), expression);
    if (!expectedType.isAssignableFrom(replacementCollectionType) && !isReplaceableType((PsiClassType)expectedType)) {
      return;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    PsiLocalVariable localVariable = tryCast(parent, PsiLocalVariable.class);
    if (localVariable != null) {
      PsiClass localVariableClass = PsiUtil.resolveClassInClassTypeOnly(localVariable.getType());
      if (localVariableClass != null && getBaseCollectionName().equals(localVariableClass.getQualifiedName())) {
        registerNewExpressionError(expression, localVariable);
      }
      else {
        registerNewExpressionError(expression);
      }
    }
    else {
      registerNewExpressionError(expression);
    }
  }

  private boolean isReplaceableType(PsiClassType classType) {
    return getReplaceableCollectionNames().stream().anyMatch(s -> PsiTypesUtil.classNameEquals(classType, s));
  }

  @NotNull
  protected abstract List<String> getUnreplaceableCollectionNames();

  @NotNull
  protected abstract List<String> getReplaceableCollectionNames();

  @NotNull
  protected abstract String getReplacementCollectionName();

  @NotNull
  protected abstract String getBaseCollectionName();

  static PsiType[] extractParameterType(@NotNull PsiLocalVariable localVariable, int expectedParameterCount) {
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
    PsiNewExpression newExpression = tryCast(initializer, PsiNewExpression.class);
    if (newExpression == null) return null;
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null || !argumentList.isEmpty()) return null;
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) return null;
    PsiReferenceParameterList parameterList = classReference.getParameterList();
    if (parameterList == null) return null;
    PsiClassType classType = tryCast(newExpression.getType(), PsiClassType.class);
    if (classType == null) return null;
    if (classType.getParameterCount() != expectedParameterCount) return null;
    return classType.getParameters();
  }
}
