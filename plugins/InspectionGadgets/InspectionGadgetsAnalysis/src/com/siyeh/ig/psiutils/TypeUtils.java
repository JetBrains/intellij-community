/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class TypeUtils {
  private static final String[] EQUAL_CONTRACT_CLASSES = {CommonClassNames.JAVA_UTIL_LIST,
    CommonClassNames.JAVA_UTIL_SET, CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_MAP_ENTRY};

  private static final Map<PsiType, Integer> typePrecisions = new HashMap<>(7);

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

  @Contract("_, null -> false")
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
    return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
  }

  public static PsiClassType getObjectType(@NotNull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  public static PsiClassType getStringType(@NotNull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_STRING, context);
  }

  /**
   * JLS 5.1.3. Narrowing Primitive Conversion
   */
  public static boolean isNarrowingConversion(@Nullable PsiType sourceType, @Nullable PsiType targetType) {
    final Integer sourcePrecision = typePrecisions.get(sourceType);
    final Integer targetPrecision = typePrecisions.get(targetType);
    return sourcePrecision != null && targetPrecision != null && targetPrecision.intValue() < sourcePrecision.intValue();
  }

  @Contract("null -> false")
  public static boolean isJavaLangObject(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_OBJECT, targetType);
  }

  @Contract("null -> false")
  public static boolean isJavaLangString(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_STRING, targetType);
  }

  public static boolean isOptional(@Nullable PsiType type) {
    return isOptional(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  @Contract("null -> false")
  public static boolean isOptional(PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    return CommonClassNames.JAVA_UTIL_OPTIONAL.equals(qualifiedName)
           || "java.util.OptionalDouble".equals(qualifiedName)
           || "java.util.OptionalInt".equals(qualifiedName)
           || "java.util.OptionalLong".equals(qualifiedName)
           || "com.google.common.base.Optional".equals(qualifiedName);
  }

  public static boolean isExpressionTypeAssignableWith(@NotNull PsiExpression expression, @NotNull Iterable<String> rhsTypeTexts) {
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    for (String rhsTypeText : rhsTypeTexts) {
      final PsiClassType rhsType = factory.createTypeByFQClassName(rhsTypeText, expression.getResolveScope());
      if (type.isAssignableFrom(rhsType)) {
        return true;
      }
    }
    return false;
  }

  @Contract("null, _ -> false")
  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull String typeName) {
    return expressionHasTypeOrSubtype(expression, new String[] {typeName}) != null;
  }

  //getTypeIfOneOfOrSubtype
  public static String expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull String... typeNames) {
    if (expression == null) {
      return null;
    }
    final PsiType type = expression instanceof PsiFunctionalExpression
                         ? ((PsiFunctionalExpression)expression).getFunctionalInterfaceType()
                         : expression.getType();
    if (type == null) {
      return null;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
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
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
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
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(variable.getType());
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

  public static boolean areConvertible(PsiType type1, PsiType type2) {
    if (TypeConversionUtil.areTypesConvertible(type1, type2)) {
      return true;
    }
    final PsiType comparedTypeErasure = TypeConversionUtil.erasure(type1);
    final PsiType comparisonTypeErasure = TypeConversionUtil.erasure(type2);
    if (comparedTypeErasure == null || comparisonTypeErasure == null ||
        TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
      if (type1 instanceof PsiClassType && type2 instanceof PsiClassType) {
        final PsiClassType classType1 = (PsiClassType)type1;
        final PsiClassType classType2 = (PsiClassType)type2;
        final PsiType[] parameters1 = classType1.getParameters();
        final PsiType[] parameters2 = classType2.getParameters();
        if (parameters1.length != parameters2.length) {
          return ((PsiClassType)type1).isRaw() || ((PsiClassType)type2).isRaw();
        }
        for (int i = 0; i < parameters1.length; i++) {
          if (!areConvertible(parameters1[i], parameters2[i])) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isTypeParameter(PsiType type) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return aClass instanceof PsiTypeParameter;
  }

  /**
   * JLS 5.6.1 Unary Numeric Promotion
   */
  public static PsiType unaryNumericPromotion(PsiType type) {
    if (type == null) {
      return null;
    }
    if (type.equalsToText("java.lang.Byte") || type.equalsToText("java.lang.Short") ||
        type.equalsToText("java.lang.Character") || type.equalsToText("java.lang.Integer") ||
        type.equals(PsiType.BYTE) || type.equals(PsiType.SHORT) || type.equals(PsiType.CHAR)) {
      return PsiType.INT;
    }
    else if (type.equalsToText("java.lang.Long")) {
      return PsiType.LONG;
    }
    else if (type.equalsToText("java.lang.Float")) {
      return PsiType.FLOAT;
    }
    else if (type.equalsToText("java.lang.Double")) {
      return PsiType.DOUBLE;
    }
    return type;
  }

  @Contract("null -> null")
  public static String resolvedClassName(PsiType type) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return aClass == null ? null : aClass.getQualifiedName();
  }

  /**
   * Returns true if instances of two given types may be equal according to equals method contract even if they belong to
   * inconvertible classes (e.g. {@code ArrayList} and {@code LinkedList}). This method does not check any type parameters that
   * may be present.
   *
   * @param type1 first type
   * @param type2 second type
   * @return true if instances of given types may be equal
   */
  public static boolean mayBeEqualByContract(PsiType type1, PsiType type2) {
    return Stream.of(EQUAL_CONTRACT_CLASSES).anyMatch(className -> areConvertibleSubtypesOf(type1, type2, className));
  }

  private static boolean areConvertibleSubtypesOf(PsiType type1, PsiType type2, String className) {
    PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(type1);
    if (class1 == null) return false;
    PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(type2);
    if (class2 == null) return false;
    PsiClass superClass = JavaPsiFacade.getInstance(class1.getProject()).findClass(className, class1.getResolveScope());
    if (superClass == null) return false;
    return InheritanceUtil.isInheritorOrSelf(class1, superClass, true) && InheritanceUtil.isInheritorOrSelf(class2, superClass, true);
  }
}