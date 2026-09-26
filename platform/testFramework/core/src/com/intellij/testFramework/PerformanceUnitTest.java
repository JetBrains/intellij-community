// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a test class or test method as a performance test to be run on CI only when the {@systemProperty idea.performance.tests} or
 * {@systemProperty idea.include.performance.tests} property is enabled.
 * <p>
 * Previously detected by {@code className.contains("Performance", ignoreCase = true)} or {@code testName.contains("Performance", ignoreCase = true)}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PerformanceUnitTest {
}
