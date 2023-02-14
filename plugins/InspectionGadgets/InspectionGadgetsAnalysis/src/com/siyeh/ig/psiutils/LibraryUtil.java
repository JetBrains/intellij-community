/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LibraryUtil {

  private LibraryUtil() {}

  public static boolean isTypeInLibrary(@Nullable PsiType type) {
    return classIsInLibrary(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  public static boolean classIsInLibrary(@Nullable PsiClass aClass) {
    return aClass instanceof PsiCompiledElement;
  }

  public static boolean callOnLibraryMethod(
    @NotNull PsiMethodCallExpression expression) {
    final PsiMethod method = expression.resolveMethod();
    return method instanceof PsiCompiledElement;
  }

  public static boolean isOverrideOfLibraryMethod(@NotNull PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      final PsiClass containingClass = superMethod.getContainingClass();
      if (classIsInLibrary(containingClass)) {
        return true;
      }
      if (isOverrideOfLibraryMethod(superMethod)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isOverrideOfLibraryMethodParameter(
    @Nullable PsiVariable variable) {
    if (!(variable instanceof PsiParameter parameter)) {
      return false;
    }
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod method)) {
      return false;
    }
    return isOverrideOfLibraryMethod(method);
  }
}