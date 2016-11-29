/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.io.IOException;
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
    try {
      TestAll.fillTestCases(loader, testPackage, TestAll.getClassRoots());
    }
    catch (IOException e) {
      throw new InitializationError(e);
    }

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
