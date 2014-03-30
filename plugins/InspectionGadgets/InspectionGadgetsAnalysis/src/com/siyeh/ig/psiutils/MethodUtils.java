/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodUtils {

  private MethodUtils() {}

  public static boolean isCompareTo(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    return methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.COMPARE_TO, PsiType.NULL);
  }

  public static boolean isHashCode(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    return methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.HASH_CODE);
  }

  public static boolean isToString(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, null, stringType, HardcodedMethodConstants.TO_STRING);
  }

  public static boolean isEquals(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType objectType = TypeUtils.getObjectType(method);
    return methodMatches(method, null, PsiType.BOOLEAN, HardcodedMethodConstants.EQUALS, objectType);
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
    if (parameterTypes != null) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != parameterTypes.length) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final PsiType type = parameter.getType();
        final PsiType parameterType = parameterTypes[i];
        if (PsiType.NULL.equals(parameterType)) {
          continue;
        }
        if (parameterType != null &&
            !EquivalenceChecker.typesAreEquivalent(type, parameterType)) {
          return false;
        }
      }
    }
    if (returnType != null) {
      final PsiType methodReturnType = method.getReturnType();
      if (!EquivalenceChecker.typesAreEquivalent(returnType, methodReturnType)) {
        return false;
      }
    }
    if (containingClassName != null) {
      final PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, containingClassName);
    }
    return true;
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
    if (parameterTypes != null) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != parameterTypes.length) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final PsiType type = parameter.getType();
        final PsiType parameterType = parameterTypes[i];
        if (PsiType.NULL.equals(parameterType)) {
          continue;
        }
        if (parameterType != null &&
            !EquivalenceChecker.typesAreEquivalent(type, parameterType)) {
          return false;
        }
      }
    }
    if (returnType != null) {
      final PsiType methodReturnType = method.getReturnType();
      if (!EquivalenceChecker.typesAreEquivalent(returnType, methodReturnType)) {
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
    if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    return SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
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
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return true;
    }
    final PsiStatement[] statements = body.getStatements();
    return statements.length == 0;
  }

  public static boolean hasInThrows(@NotNull PsiMethod method, @NotNull String... exceptions) {
    if (exceptions.length == 0) {
      throw new IllegalArgumentException("no exceptions specified");
    }
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement reference : references) {
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
}
