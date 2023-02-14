// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations

import org.jetbrains.plugins.gradle.testFramework.annotations.processors.CsvCrossProductArgumentsProcessor
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * Describes JUnit5 test parameters.
 *
 * Test with this annotation will be runned for all parameters combinations.
 *
 * For example test with annotation:
 * ```
 *   CsvCrossProductSource("""
 *     argument1-a,
 *     argument1-b,
 *     argument1-c
 *   """, """
 *     argument2-a: argument3-a,
 *     argument2-b: argument3-b
 *   """)
 * ```
 * will be runned 6 times with parameters:
 * ```
 *   argument1-a: argument2-a: argument3-a,
 *   argument1-a: argument2-b: argument3-b,
 *   argument1-b: argument2-a: argument3-a,
 *   argument1-b: argument2-b: argument3-b,
 *   argument1-c: argument2-a: argument3-a,
 *   argument1-c: argument2-b: argument3-b
 * ```
 *
 * @param value is comma-separated test parameters.
 * @param separator divides different test parameters sets.
 * @param delimiter divides parameters in one parameter set.
 *
 * @see org.junit.jupiter.params.provider.CsvSource
 * @see org.junit.jupiter.params.provider.ArgumentsSource
 * @see org.junit.jupiter.params.ParameterizedTest
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ArgumentsSource(CsvCrossProductArgumentsProcessor::class)
annotation class CsvCrossProductSource(vararg val value: String, val separator: Char = ',', val delimiter: Char = ':')
