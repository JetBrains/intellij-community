// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.platform.testFramework.junit5.eel.params.impl.junit5.EelArgumentsProvider
import com.intellij.platform.testFramework.junit5.eel.params.impl.junit5.EelCartesianArgumentsProvider
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junitpioneer.jupiter.cartesian.CartesianArgumentsSource
import kotlin.annotation.AnnotationTarget.*

/**
 * Mark your parametrized test that accepts [EelHolder]
 * See [TestApplicationWithEel]
 */
@ArgumentsSource(EelArgumentsProvider::class)
@CartesianArgumentsSource(EelCartesianArgumentsProvider::class)
@TestOnly
@Target(ANNOTATION_CLASS, VALUE_PARAMETER, CONSTRUCTOR, FUNCTION)
annotation class EelSource
