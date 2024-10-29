// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.StressTestApplicationExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Marks stress test (see [StressTestApplicationExtension])
 * ```kotlin
 * @StressTestApplication
 * class MyTest {
 *  @Test
 *  fun foo() = Unit
 * }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@TestApplication
@ExtendWith(
  StressTestApplicationExtension::class
)
annotation class StressTestApplication
