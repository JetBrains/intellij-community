/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TypeUtils {

  private static final Map<PsiType, Integer> typePrecisions = new HashMap<PsiType, Integer>(7);

  static {
    typePrecisions.put(PsiType.BYTE, 1);
    typePrecisions.put(PsiType.CHAR, 2);
    typePrecisions.put(PsiType.SHORT, 2);
    typePrecisions.put(PsiType.INT, 3);
    typePrecisions.put(PsiType.LONG, 4);
    typePrecisions.put(PsiType.FLOAT, 5);
    typePrecisions.put(PsiType.DOUBLE, 6);
  }

  private TypeUtils() {}

  public static boolean typeEquals(@NonNls @NotNull String typeName, @Nullable PsiType targetType) {
    return targetType != null && targetType.equalsToText(typeName);
  }

  public static PsiClassType getType(@NotNull String fqName, @NotNull PsiElement context) {
    final Project project = context.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final GlobalSearchScope scope = context.getResolveScope();
    return factory.createTypeByFQClassName(fqName, scope);
  }

  public static PsiClassType getType(@NotNull PsiClass aClass) {
    return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
  }

  public static PsiClassType getObjectType(@NotNull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  public static PsiClassType getStringType(@NotNull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_STRING, context);
  }

  public static boolean isNarrowingConversion(@NotNull PsiType operandType, @NotNull PsiType castType) {
    final Integer operandPrecision = typePrecisions.get(operandType);
    final Integer castPrecision = typePrecisions.get(castType);
    return operandPrecision != null && castPrecision != null && operandPrecision.intValue() > castPrecision.intValue();
  }

  public static boolean isJavaLangObject(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_OBJECT, targetType);
  }

  public static boolean isJavaLangString(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_STRING, targetType);
  }

  public static boolean isExpressionTypeAssignableWith(@NotNull PsiExpression expression, @NotNull Iterable<String> rhsTypeTexts) {
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    for (String rhsTypeText : rhsTypeTexts) {
      final PsiClassType rhsType = factory.createTypeByFQClassName(rhsTypeText, expression.getResolveScope());
      if (type.isAssignableFrom(rhsType)) {
        return true;
      }
    }
    return false;
  }

  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull String typeName) {
    return expressionHasTypeOrSubtype(expression, new String[] {typeName}) != null;
  }

  //getTypeIfOneOfOrSubtype
  public static String expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull String... typeNames) {
    if (expression == null) {
      return null;
    }
    PsiType type = expression.getType();
    if (type instanceof PsiLambdaExpressionType) {
      type = ((PsiLambdaExpressionType)type).getExpression().getFunctionalInterfaceType();
    }
    if (type == null) {
      return null;
    }
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return null;
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(aClass, typeName)) {
        return typeName;
      }
    }
    return null;
  }

  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull Iterable<String> typeNames) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(aClass, typeName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean variableHasTypeOrSubtype(@Nullable PsiVariable variable, @NonNls @NotNull String... typeNames) {
    if (variable == null) {
      return false;
    }
    final PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(aClass, typeName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasFloatingPointType(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    return type != null && (PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type));
  }
}