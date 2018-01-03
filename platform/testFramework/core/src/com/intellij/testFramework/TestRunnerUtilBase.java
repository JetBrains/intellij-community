package com.intellij.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class TestRunnerUtilBase {
  @TestOnly
  public static boolean isJUnit4TestClass(final Class aClass) {
    final int modifiers = aClass.getModifiers();
    if ((modifiers & Modifier.ABSTRACT) != 0) return false;
    if ((modifiers & Modifier.PUBLIC) == 0) return false;
    if (aClass.getAnnotation(RunWith.class) != null) return true;
    for (Method method : aClass.getMethods()) {
      if (method.getAnnotation(Test.class) != null) return true;
    }
    return false;
  }

  public static boolean isPerformanceTest(@Nullable String testName, @Nullable String className) {
    return testName != null && StringUtil.containsIgnoreCase(testName, "performance") ||
           className != null && StringUtil.containsIgnoreCase(className, "performance");
  }
}
