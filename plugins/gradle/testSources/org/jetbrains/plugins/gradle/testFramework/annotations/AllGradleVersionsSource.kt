// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations

import org.jetbrains.plugins.gradle.testFramework.annotations.processors.AllGradleVersionArgumentsProcessor
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * Alias for [GradleTestSource] where [GradleTestSource.value] are predefined with
 * [org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ArgumentsSource(AllGradleVersionArgumentsProcessor::class)
annotation class AllGradleVersionsSource(vararg val value: String)
