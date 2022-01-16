// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit5;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

@SuppressWarnings("NewClassNamingConvention")
public class JUnit5ConditionsTest {
  @Test
  void disabledConditions() {
    String[] disabledClasses = {DisabledClass.class.getName(), MetaDisabledClass.class.getName()};
    Arrays.stream(disabledClasses)
      .flatMap(klass -> Arrays.stream(new String[]{klass, klass + ",test1"}))
      .forEach(member -> Assertions.assertNotNull(JUnit5TestRunnerUtil.getDisabledConditionValue(member), member));

    String withDisabledMethodName = WithDisabledMethod.class.getName();
    Assertions.assertAll(() -> Assertions.assertNull(JUnit5TestRunnerUtil.getDisabledConditionValue(withDisabledMethodName), withDisabledMethodName),
                         () -> Assertions.assertNotNull(JUnit5TestRunnerUtil.getDisabledConditionValue(withDisabledMethodName + ",test1"), withDisabledMethodName));
  }

  static class WithDisabledMethod {
    @Disabled
    @Test
    void test1() {}
  }

  @Disabled
  static class DisabledClass {
    @Test
    void test1() {}
  }

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Disabled
  @interface MetaDisabled {}

  @MetaDisabled
  static class MetaDisabledClass {
    @Test
    void test1() {}
  }
}
