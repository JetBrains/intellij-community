// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;

import java.awt.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class TestFrameworkUtil {
  public static final boolean SKIP_HEADLESS = GraphicsEnvironment.isHeadless();
  public static final boolean SKIP_SLOW = Boolean.getBoolean("skip.slow.tests.locally");

  public static boolean canRunTest(@NotNull Class<?> testCaseClass) {
    if (!SKIP_SLOW && !SKIP_HEADLESS) {
      return true;
    }

    for (Class<?> clazz = testCaseClass; clazz != null; clazz = clazz.getSuperclass()) {
      if (SKIP_HEADLESS && clazz.getAnnotation(SkipInHeadlessEnvironment.class) != null) {
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
    }
    return false;
  }

  public static boolean isPerformanceTest(@Nullable String testName, @Nullable String className) {
    return containsWord(testName, className, "performance");
  }

  public static boolean isStressTest(@Nullable String testName, @Nullable String className) {
    return isPerformanceTest(testName, className) ||
           containsWord(testName, className, "stress") ||
           containsWord(testName, className, "slow");
  }

  private static boolean containsWord(@Nullable String testName, @Nullable String className, @NotNull String word) {
    return testName != null && StringUtil.containsIgnoreCase(testName, word) ||
           className != null && StringUtil.containsIgnoreCase(className, word);
  }
}
