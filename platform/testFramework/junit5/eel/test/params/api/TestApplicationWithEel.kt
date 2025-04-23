// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.platform.testFramework.junit5.eel.params.impl.junit5.EelInterceptor
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Mark test class:
 * ```kotlin
 * @EelTestApplication
 * class MyTest {
 *   @ParametrizedTest
 *   @EelSource // With Junit5Pioneer annotate parameter
 *   fun myTest(eh:EelHolder) {
 *   eh.eel
 *   }
 * }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  EelInterceptor::class,
)
@TestApplication
annotation class TestApplicationWithEel(val atLeastOneRemoteEelRequired: Boolean = true)