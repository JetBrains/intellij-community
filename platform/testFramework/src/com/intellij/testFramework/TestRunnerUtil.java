package com.intellij.testFramework;

import org.jetbrains.annotations.TestOnly;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author yole
 */
public class TestRunnerUtil {
  private TestRunnerUtil() {
  }

  @TestOnly
  public static boolean isJUnit4TestClass(final Class aClass) {
    final int modifiers = aClass.getModifiers();
    if ((modifiers & Modifier.ABSTRACT) != 0) return false;
    if ((modifiers & Modifier.PUBLIC) == 0) return false;
    if (aClass.isAnnotationPresent(RunWith.class)) return true;
    for (Method method : aClass.getMethods()) {
      if (method.isAnnotationPresent(Test.class)) return true;
    }
    return false;
  }
}
