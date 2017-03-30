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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Tagir Valeev
 */
public class StreamApiUtil {
  @Contract("null -> null")
  public static PsiType getStreamElementType(PsiType type) {
    return getStreamElementType(type, true);
  }

  @Contract("null, _ -> null")
  public static PsiType getStreamElementType(PsiType type, boolean variableType) {
    if(!(type instanceof PsiClassType)) return null;
    PsiClass aClass = ((PsiClassType)type).resolve();
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) {
      return PsiType.INT;
    }
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) {
      return PsiType.LONG;
    }
    if(com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) {
      return PsiType.DOUBLE;
    }
    if(!com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, false, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
      return null;
    }
    PsiType streamType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_STREAM_STREAM, 0, false);
    if (variableType) {
      if (streamType instanceof PsiIntersectionType) {
        return null;
      }
      streamType = GenericsUtil.getVariableTypeByExpressionType(streamType);
    }
    return streamType;
  }

  public static boolean isNullOrEmptyStream(PsiExpression expression) {
    if(ExpressionUtils.isNullLiteral(expression)) {
      return true;
    }
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    String name = call.getMethodExpression().getReferenceName();
    if ((!"empty".equals(name) && !"of".equals(name)) || !(call.getArgumentList().getExpressions().length == 0)) {
      return false;
    }
    PsiMethod method = call.resolveMethod();
    if (method == null || !method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if(aClass == null) return false;
    String qualifiedName = aClass.getQualifiedName();
    return qualifiedName != null && qualifiedName.startsWith("java.util.stream.");
  }

  @Contract("null -> false")
  public static boolean isSupportedStreamElement(PsiType type) {
    if(type == null) return false;
    if(type instanceof PsiPrimitiveType) {
      return type.equals(PsiType.INT) || type.equals(PsiType.LONG) || type.equals(PsiType.DOUBLE);
    }
    return true;
  }

  @NotNull
  public static String generateMapOperation(PsiVariable variable, @Nullable PsiType outType, PsiElement mapper) {
    String shortcutMappingMethod = getShortcutMappingMethod(variable, outType, mapper);
    if (shortcutMappingMethod != null) return shortcutMappingMethod.isEmpty() ? "" : "." + shortcutMappingMethod + "()";
    PsiType inType = variable.getType();
    String operationName = getMapOperationName(inType, outType);
    if(outType != null && mapper instanceof PsiArrayInitializerExpression) {
      mapper = RefactoringUtil.convertInitializerToNormalExpression((PsiExpression)mapper, outType);
    }
    String typeArgument = mapper instanceof PsiExpression ? OptionalUtil.getMapTypeArgument((PsiExpression)mapper, outType) : "";
    return "." + typeArgument + operationName +
           "(" + variable.getName() + "->" + mapper.getText() + ")";
  }

  /**
   * Returns the shortcut mapping method name
   *
   * @param variable mapper input variable
   * @param outType  output type of the mapper
   * @param mapper   mapper code
   * @return shortcut mapping name ("boxed", "asLongStream", "asDoubleStream") if applicable, empty string if it's
   * ditto mapping (no mapping is necessary at all) and null if no shortcut is applicable for given mapper
   */
  @Nullable
  public static String getShortcutMappingMethod(PsiVariable variable, @Nullable PsiType outType, PsiElement mapper) {
    if (!(mapper instanceof PsiExpression)) return null;
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(((PsiExpression)mapper));
    PsiType inType = variable.getType();
    if (expression instanceof PsiTypeCastExpression && inType instanceof PsiPrimitiveType && Objects.equals(expression.getType(), outType)) {
      expression = ((PsiTypeCastExpression)expression).getOperand();
    }
    if (ExpressionUtils.isReferenceTo(expression, variable)) {
      if (!(outType instanceof PsiPrimitiveType)) {
        return inType instanceof PsiPrimitiveType ? "boxed" : "";
      }
      if (outType.equals(inType)) {
        return "";
      }
      if (PsiType.LONG.equals(outType) && PsiType.INT.equals(inType)) {
        return "asLongStream";
      }
      if (PsiType.DOUBLE.equals(outType) && (PsiType.LONG.equals(inType) || PsiType.INT.equals(inType))) {
        return "asDoubleStream";
      }
    }
    return null;
  }

  @NotNull
  public static String getMapOperationName(PsiType inType, @Nullable PsiType outType) {
    if(outType instanceof PsiPrimitiveType) {
      if(!outType.equals(inType)) {
        if(PsiType.INT.equals(outType)) {
          return "mapToInt";
        } else if(PsiType.LONG.equals(outType)) {
          return "mapToLong";
        } else if(PsiType.DOUBLE.equals(outType)) {
          return "mapToDouble";
        }
      }
    } else if(inType instanceof PsiPrimitiveType) {
      return "mapToObj";
    }
    return "map";
  }

  @Nullable
  public static String getFlatMapOperationName(PsiType inType, PsiType outType) {
    if (!(inType instanceof PsiPrimitiveType)) {
      if (PsiType.INT.equals(outType)) {
        return "flatMapToInt";
      }
      else if (PsiType.LONG.equals(outType)) {
        return "flatMapToLong";
      }
      else if (PsiType.DOUBLE.equals(outType)) {
        return "flatMapToDouble";
      }
    } else if (!inType.equals(outType)) return null;
    return "flatMap";
  }
}
