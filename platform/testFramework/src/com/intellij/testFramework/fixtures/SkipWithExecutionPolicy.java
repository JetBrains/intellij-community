// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test as not applicable to a given test execution policy. If the value of this annotation
 * matches the {@link IdeaTestExecutionPolicy#getName()} of the current test execution policy, the test will be skipped.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipWithExecutionPolicy {
  String value() default "";
}
