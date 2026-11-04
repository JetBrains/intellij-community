// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.annotations

import com.intellij.gradle.testFramework.annotations.processors.AllGradleVersionArgumentsProcessor
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * Alias for [GradleTestSource] where [GradleTestSource.value] are predefined with
 * [com.intellij.gradle.testFramework.GradleTestVersions.SUPPORTED_GRADLE_VERSIONS]
 * and respects `gradle.versions.to.run` system property.
 */
@Retention(AnnotationRetention.RUNTIME)
@ArgumentsSource(AllGradleVersionArgumentsProcessor::class)
annotation class AllGradleVersionsSource(vararg val value: String)
