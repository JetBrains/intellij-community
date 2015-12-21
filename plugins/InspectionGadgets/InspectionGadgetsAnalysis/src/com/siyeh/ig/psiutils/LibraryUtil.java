/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LibraryUtil {

  private LibraryUtil() {}

  public static boolean isTypeInLibrary(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    return classIsInLibrary(aClass);
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
    if (!(variable instanceof PsiParameter)) {
      return false;
    }
    final PsiParameter parameter = (PsiParameter)variable;
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)scope;
    return isOverrideOfLibraryMethod(method);
  }

  public static boolean isOnlyLibraryCodeUsed(PsiElement element) {
    if (element == null) {
      return false;
    }
    final Ref<Boolean> libraryCode = Ref.create(Boolean.TRUE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!libraryCode.get().booleanValue()) {
          return;
        }
        super.visitReferenceExpression(expression);
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiCompiledElement)) {
          libraryCode.set(Boolean.FALSE);
        }
      }
    });
    return libraryCode.get().booleanValue();
  }
}