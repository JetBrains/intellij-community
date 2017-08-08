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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodUtils {

  private MethodUtils() {}

  @Contract("null -> false")
  public static boolean isComparatorCompare(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, CommonClassNames.JAVA_UTIL_COMPARATOR, PsiType.INT, "compare", null, null);
  }

  @Contract("null -> false")
  public static boolean isCompareTo(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.COMPARE_TO, PsiType.NULL)
      && InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_LANG_COMPARABLE);
  }

  @Contract("null -> false")
  public static boolean isCompareToIgnoreCase(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, "java.lang.String", PsiType.INT, "compareToIgnoreCase", stringType);
  }

  @Contract("null -> false")
  public static boolean isHashCode(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.HASH_CODE);
  }

  @Contract("null -> false")
  public static boolean isFinalize(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, null, PsiType.VOID, HardcodedMethodConstants.FINALIZE);
  }

  @Contract("null -> false")
  public static boolean isToString(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, null, stringType, HardcodedMethodConstants.TO_STRING);
  }

  @Contract("null -> false")
  public static boolean isEquals(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType objectType = TypeUtils.getObjectType(method);
    return methodMatches(method, null, PsiType.BOOLEAN, HardcodedMethodConstants.EQUALS, objectType);
  }

  @Contract("null -> false")
  public static boolean isEqualsIgnoreCase(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, "java.lang.String", PsiType.BOOLEAN, HardcodedMethodConstants.EQUALS_IGNORE_CASE, stringType);
  }

  /**
   * @param method              the method to compare to.
   * @param containingClassName the name of the class which contiains the
   *                            method.
   * @param returnType          the return type, specify null if any type matches
   * @param methodNamePattern   the name the method should have
   * @param parameterTypes      the type of the parameters of the method, specify
   *                            null if any number and type of parameters match or an empty array
   *                            to match zero parameters.
   * @return true, if the specified method matches the specified constraints,
   *         false otherwise
   */
  public static boolean methodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @Nullable PsiType returnType,
    @Nullable Pattern methodNamePattern,
    @Nullable PsiType... parameterTypes) {
    if (methodNamePattern != null) {
      final String name = method.getName();
      final Matcher matcher = methodNamePattern.matcher(name);
      if (!matcher.matches()) {
        return false;
      }
    }
    return methodMatches(method, containingClassName, returnType, parameterTypes);
  }

  /**
   * @param method              the method to compare to.
   * @param containingClassName the name of the class which contiains the
   *                            method.
   * @param returnType          the return type, specify null if any type matches
   * @param methodName          the name the method should have
   * @param parameterTypes      the type of the parameters of the method, specify
   *                            null if any number and type of parameters match or an empty array
   *                            to match zero parameters.
   * @return true, if the specified method matches the specified constraints,
   *         false otherwise
   */
  public static boolean methodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @Nullable PsiType returnType,
    @NonNls @Nullable String methodName,
    @Nullable PsiType... parameterTypes) {
    final String name = method.getName();
    if (methodName != null && !methodName.equals(name)) {
      return false;
    }
    return methodMatches(method, containingClassName, returnType, parameterTypes);
  }

  private static boolean methodMatches(@NotNull PsiMethod method,
                                       @NonNls @Nullable String containingClassName,
                                       @Nullable PsiType returnType,
                                       @Nullable PsiType... parameterTypes) {
    if (parameterTypes != null) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != parameterTypes.length) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        ProgressManager.checkCanceled();
        final PsiParameter parameter = parameters[i];
        final PsiType type = parameter.getType();
        final PsiType parameterType = parameterTypes[i];
        if (PsiType.NULL.equals(parameterType)) {
          continue;
        }
        if (parameterType != null && !EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(type, parameterType)) {
          return false;
        }
      }
    }
    if (returnType != null) {
      final PsiType methodReturnType = method.getReturnType();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(returnType, methodReturnType)) {
        return false;
      }
    }
    if (containingClassName != null) {
      final PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, containingClassName);
    }
    return true;
  }

  public static boolean simpleMethodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @NonNls @Nullable String returnTypeString,
    @NonNls @Nullable String methodName,
    @NonNls @Nullable String... parameterTypeStrings) {
    final Project project = method.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    try {
      if (parameterTypeStrings != null) {
        final PsiType[] parameterTypes = PsiType.createArray(parameterTypeStrings.length);
        for (int i = 0; i < parameterTypeStrings.length; i++) {
          ProgressManager.checkCanceled();
          final String parameterTypeString = parameterTypeStrings[i];
          parameterTypes[i] = factory.createTypeFromText(parameterTypeString, method);
        }
        if (returnTypeString != null) {
          final PsiType returnType = factory.createTypeFromText(returnTypeString, method);
          return methodMatches(method, containingClassName, returnType, methodName, parameterTypes);
        }
        else {
          return methodMatches(method, containingClassName, null, methodName, parameterTypes);
        }
      }
      else if (returnTypeString != null) {
        final PsiType returnType = factory.createTypeFromText(returnTypeString, method);
        return methodMatches(method, containingClassName, returnType, methodName);
      }
      else {
        return methodMatches(method, containingClassName, null, methodName);
      }
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean hasSuper(@NotNull PsiMethod method) {
    return getSuper(method) != null;
  }

  @Nullable
  public static PsiMethod getSuper(@NotNull PsiMethod method) {
    final MethodSignatureBackedByPsiMethod signature = getSuperMethodSignature(method);
    if (signature == null) {
      return null;
    }
    return signature.getMethod();
  }

  @Nullable
  public static MethodSignatureBackedByPsiMethod getSuperMethodSignature(@NotNull PsiMethod method) {
    if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return null;
    }
    return SuperMethodsSearch.search(method, null, true, false).findFirst();
  }

  public static boolean isOverridden(PsiMethod method) {
    if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    final Query<PsiMethod> overridingMethodQuery = OverridingMethodsSearch.search(method);
    final PsiMethod result = overridingMethodQuery.findFirst();
    return result != null;
  }

  public static boolean isOverriddenInHierarchy(PsiMethod method, PsiClass baseClass) {
    // previous implementation:
    // final Query<PsiMethod> search = OverridingMethodsSearch.search(method);
    //for (PsiMethod overridingMethod : search) {
    //    final PsiClass aClass = overridingMethod.getContainingClass();
    //    if (InheritanceUtil.isCorrectDescendant(aClass, baseClass, true)) {
    //        return true;
    //    }
    //}
    // was extremely slow and used an enormous amount of memory for clone()
    final Query<PsiClass> search = ClassInheritorsSearch.search(baseClass, baseClass.getUseScope(), true, true, true);
    for (PsiClass inheritor : search) {
      final PsiMethod overridingMethod = inheritor.findMethodBySignature(method, false);
      if (overridingMethod != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean isEmpty(PsiMethod method) {
    return ControlFlowUtils.isEmptyCodeBlock(method.getBody());
  }

  /**
   * Returns true if the method or constructor is trivial, i.e. does nothing of consequence. This is true when the method is empty, but
   * also when it is a constructor which only calls super, contains empty statements or "if (false)" statements.
   */
  public static boolean isTrivial(PsiMethod method, boolean throwIsTrivial) {
    if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      return false;
    }
    return isTrivial(method.getBody(), throwIsTrivial);
  }

  public static boolean isTrivial(PsiClassInitializer initializer) {
    return isTrivial(initializer.getBody(), false);
  }

  private static boolean isTrivial(PsiCodeBlock codeBlock, boolean throwIsTrivial) {
    if (codeBlock == null) {
      return true;
    }
    final PsiStatement[] statements = codeBlock.getStatements();
    if (statements.length == 0) {
      return true;
    }
    for (PsiStatement statement : statements) {
      ProgressManager.checkCanceled();
      if (statement instanceof PsiEmptyStatement) {
        continue;
      }
      else if (statement instanceof PsiReturnStatement) {
        final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
        final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
        if (returnValue == null || returnValue instanceof PsiLiteralExpression) {
          return true;
        }
      }
      else if (statement instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)statement;
        final PsiExpression condition = ifStatement.getCondition();
        final Object result = ExpressionUtils.computeConstantExpression(condition);
        if (result == null || !result.equals(Boolean.FALSE)) {
          return false;
        }
      }
      else if (statement instanceof PsiExpressionStatement) {
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
        final PsiExpression expression = expressionStatement.getExpression();
        if (!(expression instanceof PsiMethodCallExpression)) {
          return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        if (!PsiKeyword.SUPER.equals(methodExpression.getText())) {
          // constructor super call
          return false;
        }
      }
      else if (throwIsTrivial && statement instanceof PsiThrowStatement) {
        return true;
      }
      else {
        return false;
      }
    }
    return true;
  }

  public static boolean hasInThrows(@NotNull PsiMethod method, @NotNull String... exceptions) {
    if (exceptions.length == 0) {
      throw new IllegalArgumentException("no exceptions specified");
    }
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement reference : references) {
      ProgressManager.checkCanceled();
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        continue;
      }
      final PsiClass aClass = (PsiClass)target;
      final String qualifiedName = aClass.getQualifiedName();
      for (String exception : exceptions) {
        if (exception.equals(qualifiedName)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isChainable(PsiMethod method) {
    if (method == null) {
      return false;
    }
    if (!InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), PsiUtil.resolveClassInClassTypeOnly(method.getReturnType()), true)) {
      return false;
    }
    final PsiElement navigationElement = method.getNavigationElement();
    if (!(navigationElement instanceof PsiMethod)) {
      return false;
    }
    method = (PsiMethod)navigationElement;
    final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(method.getBody());
    if (!(lastStatement instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiReturnStatement returnStatement = (PsiReturnStatement)lastStatement;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    return returnValue instanceof PsiThisExpression;
  }

  @Contract("null -> false")
  public static boolean isStringLength(@Nullable PsiMethod method) {
    if (method == null || !method.getName().equals("length") || method.getParameterList().getParametersCount() != 0) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass != null && CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName());
  }
}
