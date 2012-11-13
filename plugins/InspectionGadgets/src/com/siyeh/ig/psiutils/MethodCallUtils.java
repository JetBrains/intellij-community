/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodCallUtils {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Set<String> regexMethodNames = new HashSet<String>(5);

  static {
    regexMethodNames.add("compile");
    regexMethodNames.add("matches");
    regexMethodNames.add("replaceFirst");
    regexMethodNames.add("replaceAll");
    regexMethodNames.add("split");
  }

  private MethodCallUtils() {}

  @Nullable
  public static String getMethodName(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression method = expression.getMethodExpression();
    return method.getReferenceName();
  }

  @Nullable
  public static PsiType getTargetType(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression method = expression.getMethodExpression();
    final PsiExpression qualifierExpression = method.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    return qualifierExpression.getType();
  }

  public static boolean isEqualsCall(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.EQUALS.equals(name)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    return MethodUtils.isEquals(method);
  }

  public static boolean isSimpleCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, @NonNls @Nullable String... parameterTypeStrings) {
    if (parameterTypeStrings == null) {
      return isCallToMethod(expression, calledOnClassName, returnType, methodName);
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(expression.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiType[] parameterTypes = new PsiType[parameterTypeStrings.length];
    final GlobalSearchScope scope = expression.getResolveScope();
    for (int i = 0; i < parameterTypeStrings.length; i++) {
      final String parameterTypeString = parameterTypeStrings[i];
      parameterTypes[i] = factory.createTypeByFQClassName(parameterTypeString, scope);
    }
    return isCallToMethod(expression, calledOnClassName, returnType, methodName, parameterTypes);
  }

  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @Nullable Pattern methodNamePattern, @Nullable PsiType... parameterTypes) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (methodNamePattern != null) {
      final String referenceName = methodExpression.getReferenceName();
      final Matcher matcher = methodNamePattern.matcher(referenceName);
      if (!matcher.matches()) {
        return false;
      }
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    if (calledOnClassName != null) {
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, calledOnClassName)) {
          return false;
        }
        return MethodUtils.methodMatches(method, null, returnType, methodNamePattern, parameterTypes);
      }
    }
    return MethodUtils.methodMatches(method, calledOnClassName, returnType, methodNamePattern, parameterTypes);
  }

  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, @Nullable PsiType... parameterTypes) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (methodName != null) {
      final String referenceName = methodExpression.getReferenceName();
      if (!methodName.equals(referenceName)) {
        return false;
      }
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    return MethodUtils.methodMatches(method, calledOnClassName, returnType, methodName, parameterTypes);
  }

  public static boolean isApplicable(PsiMethod method, PsiSubstitutor substitutorForMethod, PsiType[] types) {
    final PsiParameterList parameterList = method.getParameterList();
    if (method.isVarArgs()) {
      if (types.length < parameterList.getParametersCount() - 1) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      PsiType lastParameterType = lastParameter.getType();
      if (!(lastParameterType instanceof PsiArrayType)) {
        return false;
      }
      lastParameterType = substitutorForMethod.substitute(lastParameterType);
      if (lastParameter.isVarArgs()) {
        for (int i = 0; i < parameters.length - 1; i++) {
          final PsiParameter parameter = parameters[i];
          if (parameter.isVarArgs()) {
            return false;
          }
          final PsiType argumentType = types[i];
          if (argumentType == null) {
            return false;
          }
          final PsiType parameterType = parameters[i].getType();
          final PsiType substitutedParameterType = substitutorForMethod.substitute(parameterType);
          if (!TypeConversionUtil.isAssignable(substitutedParameterType, argumentType)) {
            return false;
          }
        }
        if (types.length == parameters.length) {
          //call with array as vararg parameter
          final PsiType lastArgType = types[types.length - 1];
          if (lastArgType != null && TypeConversionUtil.isAssignable(lastParameterType, lastArgType)) {
            return true;
          }
        }
        final PsiArrayType arrayType = (PsiArrayType)lastParameterType;
        final PsiType componentType = arrayType.getComponentType();
        for (int i = parameters.length - 1; i < types.length; i++) {
          final PsiType argType = types[i];
          if (argType == null || !TypeConversionUtil.isAssignable(componentType, argType)) {
            return false;
          }
        }
      }
      else {
        return false;
      }
    }
    else {
      if (types.length != parameterList.getParametersCount()) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < types.length; i++) {
        final PsiType type = types[i];
        if (type == null) {
          return false; //?
        }
        final PsiType parameterType = parameters[i].getType();
        final PsiType substitutedParameterType = substitutorForMethod.substitute(parameterType);
        if (!TypeConversionUtil.isAssignable(substitutedParameterType, type)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean isCallToRegexMethod(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!regexMethodNames.contains(name)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String className = containingClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_STRING.equals(className) || "java.util.regex.Pattern".equals(className);
  }

  public static boolean isCallDuringObjectConstruction(PsiMethodCallExpression expression) {
    final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiClassInitializer.class, PsiField.class);
    if (member == null) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      if (!(qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression)) {
        return false;
      }
    }
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (member instanceof PsiClassInitializer) {
      final PsiClassInitializer classInitializer = (PsiClassInitializer)member;
      if (!classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    else if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      if (method.isConstructor()) {
        return true;
      }
      if (CloneUtils.isClone(method)) {
        return true;
      }
      if (MethodUtils.simpleMethodMatches(method, null, "void", "readObject", "java.io.ObjectInputStream")) {
        return true;
      }
      return MethodUtils.simpleMethodMatches(method, null, "void", "readObjectNoData");
    }
    else if (member instanceof PsiField) {
      final PsiField field = (PsiField)member;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }
}