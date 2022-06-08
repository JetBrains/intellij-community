// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations

import org.jetbrains.plugins.gradle.testFramework.annotations.processors.CsvCrossProductArgumentsProcessor
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * Describes JUnit5 test parameters.
 *
 * Test with this annotation will be runned for all parameters combinations.
 *
 * For example: Test with annotation `@CsvCrossProductSource({{1, 2}, {a, b}})`
 * will be runned 4 times with parameters: `{1, a}`, `{1, b}`, `{2, a}` and `{2, b}`.
 *
 * @param value is comma-separated test parameters.
 * @param separator is parameter delimiter character.
 *
 * @see org.junit.jupiter.params.provider.CsvSource
 * @see org.junit.jupiter.params.provider.ArgumentsSource
 * @see org.junit.jupiter.params.ParameterizedTest
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ArgumentsSource(CsvCrossProductArgumentsProcessor::class)
annotation class CsvCrossProductSource(vararg val value: String, val separator: Char = ',')
