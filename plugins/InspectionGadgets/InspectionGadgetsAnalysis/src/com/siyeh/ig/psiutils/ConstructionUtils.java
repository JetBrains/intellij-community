// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ConstructionUtils {
  private static final Set<String> GUAVA_UTILITY_CLASSES =
    ContainerUtil.set("com.google.common.collect.Maps", "com.google.common.collect.Lists", "com.google.common.collect.Sets");
  private static final CallMatcher ENUM_SET_NONE_OF =
    CallMatcher.staticCall("java.util.EnumSet", "noneOf").parameterCount(1);

  /**
   * Checks that given expression initializes empty StringBuilder or StringBuffer (either with explicit default capacity or not)
   *
   * @param initializer initializer to check
   * @return true if the initializer is empty StringBuilder or StringBuffer initializer
   */
  @Contract("null -> false")
  public static boolean isEmptyStringBuilderInitializer(PsiExpression initializer) {
    return "\"\"".equals(getStringBuilderInitializerText(initializer));
  }

  /**
   * Returns a textual representation of an expression which is equivalent to the initial value of newly created StringBuilder or StringBuffer
   *
   * @param construction StringBuilder/StringBuffer construction expression
   * @return a textual representation of an initial value CharSequence or null if supplied expression is not StringBuilder/StringBuffer
   * construction expression
   */
  @Contract("null -> null")
  public static String getStringBuilderInitializerText(PsiExpression construction) {
    construction = PsiUtil.skipParenthesizedExprDown(construction);
    if (!(construction instanceof PsiNewExpression)) return null;
    final PsiNewExpression newExpression = (PsiNewExpression)construction;
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (!isReferenceTo(classReference, CommonClassNames.JAVA_LANG_STRING_BUILDER, CommonClassNames.JAVA_LANG_STRING_BUFFER)) {
      return null;
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return null;
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) return "\"\"";
    if (arguments.length != 1) return null;
    final PsiExpression argument = arguments[0];
    final PsiType argumentType = argument.getType();
    if (PsiType.INT.equals(argumentType)) return "\"\"";
    return argument.getText();
  }

  /**
   * Checks that given expression initializes empty Collection or Map
   *
   * @param expression expression to check
   * @return true if the expression is the empty Collection or Map initializer
   */
  @Contract("null -> false")
  public static boolean isEmptyCollectionInitializer(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)expression;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList != null && argumentList.isEmpty() && newExpression.getAnonymousClass() == null) {
        PsiClassType type = ObjectUtils.tryCast(expression.getType(), PsiClassType.class);
        if (type == null) return false;
        PsiClass aClass = type.resolve();
        if (aClass == null) return false;
        String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith("java.util.")) return false;
        return com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) ||
               com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      @NonNls String name = call.getMethodExpression().getReferenceName();
      PsiExpressionList argumentList = call.getArgumentList();
      if(name != null && name.startsWith("new") && argumentList.isEmpty()) {
        PsiMethod method = call.resolveMethod();
        if(method != null && method.getParameterList().isEmpty()) {
          PsiClass aClass = method.getContainingClass();
          if(aClass != null) {
            String qualifiedName = aClass.getQualifiedName();
            if (GUAVA_UTILITY_CLASSES.contains(qualifiedName)) {
              return true;
            }
          }
        }
      }
    }
    return isCustomizedEmptyCollectionInitializer(expression);
  }

  public static boolean isPrepopulatedCollectionInitializer(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiNewExpression) {
      PsiExpressionList args = ((PsiNewExpression)expression).getArgumentList();
      if (args == null || args.isEmpty()) return false;
      PsiMethod ctor = ((PsiNewExpression)expression).resolveMethod();
      if (ctor == null) return false;
      PsiClass aClass = ctor.getContainingClass();
      if (aClass == null) return false;
      String name = aClass.getQualifiedName();
      if (name == null || !name.startsWith("java.util.")) return false;
      for (PsiParameter parameter : ctor.getParameterList().getParameters()) {
        PsiType type = parameter.getType();
        if (PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_UTIL_COLLECTION) ||
            PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_UTIL_MAP)) {
          return true;
        }
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      @NonNls String name = call.getMethodExpression().getReferenceName();
      PsiExpressionList argumentList = call.getArgumentList();
      if(name != null && name.startsWith("new") && !argumentList.isEmpty()) {
        PsiMethod method = call.resolveMethod();
        if (method == null) return false;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return false;
        String qualifiedName = aClass.getQualifiedName();
        if (!GUAVA_UTILITY_CLASSES.contains(qualifiedName)) return false;
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
          PsiType type = parameter.getType();
          if (type instanceof PsiEllipsisType) {
            return true;
          }
          if (PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_LANG_ITERABLE) ||
              PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_UTIL_ITERATOR)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Checks that given expression initializes empty Collection or Map with custom initial capacity or load factor
   *
   * @param expression expression to check
   * @return true if the expression is the empty Collection or Map initializer with custom initial capacity or load factor
   */
  @Contract("null -> false")
  public static boolean isCustomizedEmptyCollectionInitializer(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiNewExpression) {
      PsiExpressionList argumentList = ((PsiNewExpression)expression).getArgumentList();
      if (argumentList == null || argumentList.isEmpty() || ((PsiNewExpression)expression).getAnonymousClass() != null) return false;
      PsiMethod constructor = ((PsiNewExpression)expression).resolveConstructor();
      if (constructor == null) return false;
      PsiClass aClass = constructor.getContainingClass();
      if (aClass != null && (aClass.getQualifiedName() == null || !aClass.getQualifiedName().startsWith("java.util."))) return false;
      if (!com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) &&
          !com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
        return false;
      }
      Predicate<PsiType> allowedParameterType = t -> t instanceof PsiPrimitiveType ||
                                                     com.intellij.psi.util.InheritanceUtil.isInheritor(t, CommonClassNames.JAVA_LANG_CLASS);
      return Stream.of(constructor.getParameterList().getParameters()).map(PsiParameter::getType).allMatch(allowedParameterType);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (ENUM_SET_NONE_OF.test(call)) return true;
      @NonNls String name = call.getMethodExpression().getReferenceName();
      PsiExpressionList argumentList = call.getArgumentList();
      if (name != null && name.startsWith("new") && !argumentList.isEmpty()) {
        PsiMethod method = call.resolveMethod();
        if (method != null && !method.getParameterList().isEmpty()) {
          PsiClass aClass = method.getContainingClass();
          if (aClass != null) {
            String qualifiedName = aClass.getQualifiedName();
            if (GUAVA_UTILITY_CLASSES.contains(qualifiedName)) {
              return ContainerUtil.and(method.getParameterList().getParameters(), p -> p.getType() instanceof PsiPrimitiveType);
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns true if given expression is an empty array initializer
   *
   * @param expression expression to test
   * @return true if supplied expression is an empty array initializer
   */
  public static boolean isEmptyArrayInitializer(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiNewExpression)) return false;
    final PsiNewExpression newExpression = (PsiNewExpression)expression;
    final PsiExpression[] dimensions = newExpression.getArrayDimensions();
    if (dimensions.length == 0) {
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      if (arrayInitializer == null) return false;
      final PsiExpression[] initializers = arrayInitializer.getInitializers();
      return initializers.length == 0;
    }
    for (PsiExpression dimension : dimensions) {
      final String dimensionText = dimension.getText();
      if (!"0".equals(dimensionText)) return false;
    }
    return true;
  }

  public static boolean isReferenceTo(PsiJavaCodeReferenceElement ref, String... classNames) {
    if(ref == null) return false;
    String name = ref.getReferenceName();
    if (name == null) return false;
    String qualifiedName = null;
    for (String className : classNames) {
      if(StringUtil.getShortName(className).equals(name)) {
        if (qualifiedName == null) {
          // Defer resolution if possible
          qualifiedName = ref.getQualifiedName();
        }
        if (className.equals(qualifiedName)) return true;
      }
    }
    return false;
  }

  /**
   * Checks whether given class represents a Collection or a Map and known to have a copy constructor
   *
   * @param aClass class to check
   * @return true if given class represents a Collection or a Map and known to have a copy constructor
   */
  @Contract("null -> false")
  public static boolean isCollectionWithCopyConstructor(PsiClass aClass) {
    if (aClass == null) return false;
    String name = aClass.getQualifiedName();
    return name != null && name.startsWith("java.util.") &&
           ContainerUtil.or(aClass.getConstructors(), ConstructionUtils::isCollectionConstructor);
  }

  /**
   * @param ctor method to check
   * @return true if given method is a constructor that accepts a collection or map
   */
  @Contract("null -> false")
  public static boolean isCollectionConstructor(PsiMethod ctor) {
    if (ctor == null || !ctor.isConstructor() || !ctor.getModifierList().hasExplicitModifier(PsiModifier.PUBLIC)) return false;
    PsiParameterList list = ctor.getParameterList();
    if (list.getParametersCount() != 1) return false;
    PsiType type = Objects.requireNonNull(list.getParameter(0)).getType();
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return aClass != null &&
           (CommonClassNames.JAVA_UTIL_COLLECTION.equals(aClass.getQualifiedName()) ||
            CommonClassNames.JAVA_UTIL_MAP.equals(aClass.getQualifiedName()));
  }
}
