/*
 * Copyright 2005-2015 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.FunctionalExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SuspiciousToArrayCallInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.to.array.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final PsiType foundType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("suspicious.to.array.call.problem.descriptor", type.getCanonicalText(), foundType.getCanonicalText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousToArrayCallVisitor();
  }

  private static class SuspiciousToArrayCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"toArray".equals(methodName)) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      final PsiType type = qualifierExpression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];

      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        PsiType itemType =
          GenericsUtil.getVariableTypeByExpressionType(JavaGenericsUtil.getCollectionItemType(classType, expression.getResolveScope()));
        checkArrayTypes(argument, expression, argument.getType(), itemType);
      }
      else if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
        PsiType argumentType = getIntFunctionParameterType(argument);
        if (argumentType != null) {
          checkArrayTypes(argument, expression, argumentType, StreamApiUtil.getStreamElementType(classType));
        }
      }
    }

    private static PsiType getIntFunctionParameterType(PsiExpression argument) {
      PsiType argumentType = FunctionalExpressionUtils.getFunctionalExpressionType(argument);
      return PsiUtil.substituteTypeParameter(argumentType, "java.util.function.IntFunction", 0, false);
    }

    private void checkArrayTypes(@NotNull PsiExpression argument,
                                 @NotNull PsiMethodCallExpression expression,
                                 PsiType argumentType, 
                                 PsiType itemType) {
      if (!(argumentType instanceof PsiArrayType)) {
        return;
      }
      final PsiArrayType arrayType = (PsiArrayType)argumentType;
      final PsiType componentType = arrayType.getComponentType();
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)parent;
        final PsiTypeElement castTypeElement = castExpression.getCastType();
        if (castTypeElement == null) {
          return;
        }
        final PsiType castType = castTypeElement.getType();
        if (castType.equals(arrayType) || !(castType instanceof PsiArrayType)) {
          return;
        }
        final PsiArrayType castArrayType = (PsiArrayType)castType;
        registerError(argument, castArrayType.getComponentType(), componentType);
      }
      else {
        if (itemType == null || componentType.isAssignableFrom(itemType)) {
          return;
        }
        if (itemType instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)itemType;
          final PsiClass aClass = classType.resolve();
          if (aClass instanceof PsiTypeParameter) {
            final PsiTypeParameter typeParameter = (PsiTypeParameter)aClass;
            final PsiReferenceList extendsList = typeParameter.getExtendsList();
            final PsiClassType[] types = extendsList.getReferencedTypes();
            if (types.length == 0) {
              registerError(argument, TypeUtils.getObjectType(argument), componentType);
            }
            else if (types.length == 1) {
              registerError(argument, types[0], componentType);
            }
            return;
          }
        }
        registerError(argument, itemType, componentType);
      }
    }
  }
}