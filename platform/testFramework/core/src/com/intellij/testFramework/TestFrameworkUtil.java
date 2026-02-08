// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;

import java.awt.GraphicsEnvironment;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class TestFrameworkUtil {
  public static boolean shouldSkipHeadless() { return GraphicsEnvironment.isHeadless(); }  // lazy to avoid caching GraphicsEnvironment.isHeadless() on class initialization
  public static final boolean SKIP_SLOW = Boolean.getBoolean("skip.slow.tests.locally");

  public static boolean canRunTest(@NotNull Class<?> testCaseClass) {
    if (!SKIP_SLOW && !shouldSkipHeadless()) {
      return true;
    }

    for (Class<?> clazz = testCaseClass; clazz != null; clazz = clazz.getSuperclass()) {
      if (shouldSkipHeadless() && clazz.getAnnotation(SkipInHeadlessEnvironment.class) != null) {
        System.out.println("Class '" + testCaseClass.getName() + "' is skipped because it requires working UI environment");
        return false;
      }
      if (SKIP_SLOW && clazz.getAnnotation(SkipSlowTestLocally.class) != null) {
        System.out.println("Class '" + testCaseClass.getName() + "' is skipped because it is dog slow");
        return false;
      }
    }

    return true;
  }

  @TestOnly
  public static boolean isJUnit4TestClass(@NotNull Class<?> aClass, boolean allowAbstract) {
    int modifiers = aClass.getModifiers();
    if (!allowAbstract && Modifier.isAbstract(modifiers)) return false;
    if (!Modifier.isPublic(modifiers)) return false;
    if (aClass.getAnnotation(RunWith.class) != null) return true;
    for (Method method : aClass.getMethods()) {
      if (method.getAnnotation(Test.class) != null) return true;
    }
    return false;
  }

  @TestOnly
  public static boolean isJUnit5TestClass(@NotNull Class<?> aClass, boolean allowAbstract) {
    int modifiers = aClass.getModifiers();
    if (!allowAbstract && Modifier.isAbstract(modifiers)) return false;
    if (!Modifier.isPublic(modifiers)) return false;

    if (aClass.getAnnotation(ExtendWith.class) != null) return true;
    for (Method method : aClass.getMethods()) {
      if (method.getAnnotation(org.junit.jupiter.api.Test.class) != null) return true;
      if (method.getAnnotation(TestFactory.class) != null) return true;
      if (method.getAnnotation(TestTemplate.class) != null) return true;
      if (method.getAnnotation(RepeatedTest.class) != null) return true;
      for (Annotation annotation : method.getAnnotations()) {
        String name = annotation.annotationType().getCanonicalName();
        if ("org.junit.jupiter.params.ParameterizedTest".equals(name)) return true;
      }
    }
    return false;
  }

  public static boolean isPerformanceTest(@Nullable String testName, @NotNull Class<?> aClass) {
    if (aClass.isAnnotationPresent(PerformanceUnitTest.class)) {
      return true;
    }

    if (testName != null) {
      List<Method> methods = ContainerUtil.findAll(aClass.getMethods(), method -> method.getName().equals(testName));
      if (methods.isEmpty()) {
        return false;  // not supported, e.g. org.angular2.lang.html.Angular2HtmlLexerSpecTest#`HtmlLexer, line/column numbers, it should work without newlines`
      }

      if (ContainerUtil.all(methods, method -> method.isAnnotationPresent(PerformanceUnitTest.class))) {
        return true;
      }
      else if (ContainerUtil.exists(methods, method -> method.isAnnotationPresent(PerformanceUnitTest.class))) {
        throw new IllegalStateException("Overloaded methods with inconsistent @PerformanceUnitTest annotations are not supported: " + aClass.getName() + "#" + testName);  // not supported
      }
    }

    return false;
  }

  public static boolean isStressTest(@Nullable String testName, @NotNull Class<?> aClass) {
    return isPerformanceTest(testName, aClass) ||
           containsWord(testName, aClass.getSimpleName(), "stress") ||
           containsWord(testName, aClass.getSimpleName(), "slow");
  }

  private static boolean containsWord(@Nullable String testName, @Nullable String className, @NotNull String word) {
    return testName != null && StringUtil.containsIgnoreCase(testName, word) ||
           className != null && StringUtil.containsIgnoreCase(className, word);
  }
}
