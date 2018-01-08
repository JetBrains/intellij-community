/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.util;

import com.intellij.TestAll;
import com.intellij.TestCaseLoader;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Runner that allows both JUnit 3 and JUnit 4 tests in one suite, this is the main difference from the {@link org.junit.runners.AllTests}.
 */
public class AllTestsSuite extends Suite {

  public AllTestsSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
    super(builder, klass, getSuiteClasses(klass));
  }

  private static Class<?>[] getSuiteClasses(Class<?> klass) throws InitializationError {
    TestPackage annotation = klass.getAnnotation(TestPackage.class);
    if (annotation == null) throw new InitializationError("No test package specified");

    String testPackage = annotation.value();
    SlowPolicy policy = annotation.policy();

    TestCaseLoader loader = new TestCaseLoader("", true);
    loader.fillTestCases(testPackage, TestAll.getClassRoots());

    List<Class<?>> result = ContainerUtil.newArrayList();
    for (Class aClass : loader.getClasses()) {
      if (policy == SlowPolicy.ALL) {
        result.add(aClass);
      }
      else {
        boolean slow = isSlow(aClass);
        if (slow && policy == SlowPolicy.SLOW_ONLY || !slow && policy == SlowPolicy.FAST_ONLY) {
          result.add(aClass);
        }
      }
    }
    return result.toArray(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  private static boolean isSlow(Class aClass) {
    return getAnnotationInHierarchy(aClass, Slow.class) != null;
  }

  @Nullable
  private static <T extends Annotation> T getAnnotationInHierarchy(@NotNull Class<?> clazz, @NotNull Class<T> annotationClass) {
    Class<?> current = clazz;
    while (current != null) {
      T annotation = current.getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
      current = current.getSuperclass();
    }
    return null;
  }
}
