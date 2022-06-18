// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations

import org.jetbrains.plugins.gradle.testFramework.annotations.processors.GradleTestArgumentsProcessor
import org.junit.jupiter.params.provider.ArgumentsSource


/**
 * Describes JUnit5 test parameters for Gradle.
 *
 * Test with this annotation will be runned for all arguments combinations.
 *
 * For example test with annotation:
 * ```
 *   GradleTestSource("6.9, 7.0, 7.4", """
 *     argument2-a: argument3-a,
 *     argument2-b: argument3-b
 *   """)
 * ```
 * will be runned 6 times with parameters:
 * ```
 *   6.9: argument2-a: argument3-a,
 *   6.9: argument2-b: argument3-b,
 *   7.0: argument2-a: argument3-a,
 *   7.0: argument2-b: argument3-b,
 *   7.4: argument2-a: argument3-a,
 *   7.4: argument2-b: argument3-b
 * ```
 *
 * @param value is comma-separated Gradle versions. First test parameter will
 * be automatically converted from [String] version to [org.gradle.util.GradleVersion].
 * @param values are an additional comma-separated test parameters.
 * @param separator divides different test parameters sets.
 * @param delimiter divides parameters in one parameter set.
 *
 * @see CsvCrossProductSource
 * @see AllGradleVersionsSource
 * @see BaseGradleVersionSource
 *
 * @see org.junit.jupiter.params.provider.CsvSource
 * @see org.junit.jupiter.params.provider.ArgumentsSource
 * @see org.junit.jupiter.params.ParameterizedTest
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ArgumentsSource(GradleTestArgumentsProcessor::class)
annotation class GradleTestSource(val value: String, vararg val values: String, val separator: Char = ',', val delimiter: Char = ':')
