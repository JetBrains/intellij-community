// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedClass

@ParameterizedClass(allowZeroInvocations = true)
@KmpVersionsSource
@ExtendWith(AndroidAdbThreadExtension::class)
@ExtendWith(PluginTargetVersionsExtension::class)
annotation class KmpParametrizedClass


@TestApplication
@ExtendWith(KotlinSdkExtension::class)
annotation class K2TestApplication
