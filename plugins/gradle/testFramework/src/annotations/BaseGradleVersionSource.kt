// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.annotations

import com.intellij.gradle.testFramework.annotations.processors.BaseGradleVersionArgumentsProcessor
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * Alias for [GradleTestSource] where [GradleTestSource.value] are predefined with
 * [com.intellij.gradle.testFramework.GradleTestVersions.BASE_GRADLE_VERSION].
 */
@Retention(AnnotationRetention.RUNTIME)
@ArgumentsSource(BaseGradleVersionArgumentsProcessor::class)
annotation class BaseGradleVersionSource(vararg val value: String)
