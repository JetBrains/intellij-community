// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * The annotation includes [TestApplication].
 * Do not put [TestApplication] before it: then the fixture extension comes first, and the class fixtures start outside the stress mode.
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  StressTestApplicationExtension::class
)
@TestApplication
annotation class StressTestApplication
