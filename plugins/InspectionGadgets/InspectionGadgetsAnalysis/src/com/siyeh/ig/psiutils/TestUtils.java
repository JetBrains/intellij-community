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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestUtils {

  private TestUtils() {
  }

  public static boolean isInTestSourceContent(@Nullable PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile file = element.getContainingFile();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return true;
    }
    final Project project = element.getProject();
    final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    final ProjectFileIndex fileIndex = rootManager.getFileIndex();
    return fileIndex.isInTestSourceContent(virtualFile);
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
    if (method == null) {
      return false;
    }
    if (isJUnit4TestMethod(method)) {
      return true;
    }
    final String methodName = method.getName();
    @NonNls final String test = "test";
    if (!methodName.startsWith(test)) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    final PsiType returnType = method.getReturnType();
    if (returnType == null) {
      return false;
    }
    if (!returnType.equals(PsiType.VOID)) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) {
      return false;
    }
    final PsiClass targetClass = method.getContainingClass();
    return isJUnitTestClass(targetClass);
  }

  public static boolean isJUnit4TestMethod(@Nullable PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, "org.junit.Test", true);
  }

  public static boolean isJUnitTestClass(@Nullable PsiClass targetClass) {
    return targetClass != null && InheritanceUtil.isInheritor(targetClass, "junit.framework.TestCase");
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
