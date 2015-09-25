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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestUtils {
  public static final String RUN_WITH = "org.junit.runner.RunWith";

  private TestUtils() {
  }

  public static boolean isInTestSourceContent(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile file = element.getContainingFile();
    final VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    return virtualFile != null && ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInTestSourceContent(virtualFile);
  }

  public static boolean isPartOfJUnitTestMethod(@NotNull PsiElement element) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    return method != null && isJUnitTestMethod(method);
  }

  public static boolean isJUnit4BeforeOrAfterMethod(
    @NotNull PsiMethod method) {
    return AnnotationUtil.isAnnotated(method, "org.junit.Before", true) ||
           AnnotationUtil.isAnnotated(method, "org.junit.After", true);
  }

  public static boolean isJUnit4BeforeClassOrAfterClassMethod(
    @NotNull PsiMethod method) {
    return AnnotationUtil.isAnnotated(method, "org.junit.BeforeClass", true) ||
           AnnotationUtil.isAnnotated(method, "org.junit.AfterClass", true);
  }

  public static boolean isJUnitTestMethod(@Nullable PsiMethod method) {
    return isRunnable(method) && (isJUnit3TestMethod(method) || isJUnit4TestMethod(method));
  }

  public static boolean isRunnable(PsiMethod method) {
    if (method == null) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        method.hasModifierProperty(PsiModifier.STATIC) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    final PsiType returnType = method.getReturnType();
    if (!PsiType.VOID.equals(returnType)) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    return parameterList.getParametersCount() == 0;
  }

  public static boolean isJUnit3TestMethod(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final String methodName = method.getName();
    @NonNls final String test = "test";
    if (!methodName.startsWith(test)) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    return isJUnitTestClass(containingClass);
  }

  public static boolean isJUnit4TestMethod(@Nullable PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, "org.junit.Test", true);
  }

  public static boolean isJUnitTestClass(@Nullable PsiClass targetClass) {
    return targetClass != null && InheritanceUtil.isInheritor(targetClass, "junit.framework.TestCase");
  }

  public static boolean isJUnit4TestClass(@Nullable PsiClass aClass, boolean runWithIsTestClass) {
    if (aClass == null) return false;
    if (AnnotationUtil.isAnnotated(aClass, RUN_WITH, true)) return runWithIsTestClass;
    for (final PsiMethod method : aClass.getAllMethods()) {
      if (isJUnit4TestMethod(method)) return true;
    }
    return false;
  }

  public static boolean isInTestCode(PsiElement element) {
    if (isPartOfJUnitTestMethod(element)) {
      return true;
    }
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (containingClass != null && TestFrameworks.getInstance().isTestOrConfig(containingClass)) {
      return true;
    }
    return isInTestSourceContent(element);
  }
}
