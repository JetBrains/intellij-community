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
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.hash.HashSet;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class TestUtils {
  public static final String RUN_WITH = "org.junit.runner.RunWith";

  private static final CallMatcher ASSERT_THROWS =
    CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertThrows");

  private TestUtils() { }

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

  public static boolean isJUnit4BeforeOrAfterMethod(@NotNull PsiMethod method) {
    return AnnotationUtil.isAnnotated(method, "org.junit.Before", CHECK_HIERARCHY) ||
           AnnotationUtil.isAnnotated(method, "org.junit.After", CHECK_HIERARCHY);
  }

  public static boolean isJUnitTestMethod(@Nullable PsiMethod method) {
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final Set<TestFramework> frameworks = TestFrameworks.detectApplicableFrameworks(containingClass);
    return frameworks.stream().anyMatch(framework -> framework.getName().startsWith("JUnit") && framework.isTestMethod(method, false));
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
    if (!methodName.startsWith(test) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC) && method.getParameterList().getParametersCount() > 0) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    return isJUnitTestClass(containingClass);
  }

  public static boolean isJUnit4TestMethod(@Nullable PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, JUnitCommonClassNames.ORG_JUNIT_TEST, CHECK_HIERARCHY);
  }

  public static boolean isAnnotatedTestMethod(@Nullable PsiMethod method) {
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final TestFramework testFramework = TestFrameworks.detectFramework(containingClass);
    if (testFramework == null) return false;
    if (testFramework.isTestMethod(method, false)) {
      final String testFrameworkName = testFramework.getName();
      return testFrameworkName.equals("JUnit4") || testFrameworkName.equals("JUnit5");
    }
    return false;
  }



  public static boolean isJUnitTestClass(@Nullable PsiClass targetClass) {
    return targetClass != null && InheritanceUtil.isInheritor(targetClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE);
  }

  public static boolean isJUnit4TestClass(@Nullable PsiClass aClass, boolean runWithIsTestClass) {
    if (aClass == null) return false;
    if (AnnotationUtil.isAnnotated(aClass, RUN_WITH, CHECK_HIERARCHY)) return runWithIsTestClass;
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

  /**
   * @return true if class is annotated with {@code @TestInstance(TestInstance.Lifecycle.PER_CLASS)}
   */
  public static boolean testInstancePerClass(@NotNull PsiClass containingClass) {
    return testInstancePerClass(containingClass, new HashSet<>());
  }

  private static boolean testInstancePerClass(@NotNull PsiClass containingClass, HashSet<PsiClass> classes) {
    PsiAnnotation annotation = MetaAnnotationUtil.findMetaAnnotations(containingClass, Collections.singletonList(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_INSTANCE))
      .findFirst().orElse(null);
    if (annotation != null) {
      PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value != null && value.getText().contains("PER_CLASS")) {
        return true;
      }
    }
    else {
      for (PsiClass superClass : containingClass.getSupers()) {
        if (classes.add(superClass) && testInstancePerClass(superClass, classes)) return true;
      }
    }
    return false;
  }

  /**
   * Tries to determine whether exception is expected at given element (e.g. element is a part of method annotated with
   * {@code @Test(expected = ...)} or part of lambda passed to {@code Assertions.assertThrows()}.
   *
   * Note that the test is not exhaustive: false positives and false negatives are possible.
   *
   * @param element to check
   * @return true if it's likely that exception is expected at this point.
   */
  public static boolean isExceptionExpected(PsiElement element) {
    if (!isInTestSourceContent(element)) return false;
    for(; element != null && !(element instanceof PsiFile); element = element.getParent()) {
      if (element instanceof PsiMethod) {
        return hasExpectedExceptionAnnotation((PsiMethod)element);
      }
      if (element instanceof PsiLambdaExpression) {
        PsiExpressionList expressionList =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(element.getParent()), PsiExpressionList.class);
        if (expressionList != null) {
          PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiMethodCallExpression && ASSERT_THROWS.test((PsiMethodCallExpression)parent)) return true;
        }
      }
      if (element instanceof PsiTryStatement && ((PsiTryStatement)element).getCatchBlocks().length > 0) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasExpectedExceptionAnnotation(PsiMethod method) {
    final PsiModifierList modifierList = method.getModifierList();
    return hasAnnotationWithParameter(modifierList, "org.junit.Test", "expected") ||
           hasAnnotationWithParameter(modifierList, "org.testng.annotations.Test", "expectedExceptions");
  }

  private static boolean hasAnnotationWithParameter(PsiModifierList modifierList, String annotationName, String expectedParameterName) {
    final PsiAnnotation testAnnotation = modifierList.findAnnotation(annotationName);
    if (testAnnotation == null) {
      return false;
    }
    final PsiAnnotationParameterList parameterList = testAnnotation.getParameterList();
    final PsiNameValuePair[] nameValuePairs = parameterList.getAttributes();
    for (PsiNameValuePair nameValuePair : nameValuePairs) {
      @NonNls final String parameterName = nameValuePair.getName();
      if (expectedParameterName.equals(parameterName)) {
        return true;
      }
    }
    return false;
  }
}
