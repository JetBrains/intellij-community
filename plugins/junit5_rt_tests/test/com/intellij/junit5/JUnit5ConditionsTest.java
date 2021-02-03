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
package com.intellij.junit5;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

@SuppressWarnings("NewClassNamingConvention")
@RunWith(JUnitPlatform.class)
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
