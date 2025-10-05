// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.navigation

import org.jetbrains.kotlin.gradle.KotlinGradleGotoDependenciesSourcesTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class K2GradleNavigationToDependencySourceTest : KotlinGradleGotoDependenciesSourcesTest()