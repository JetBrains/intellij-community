// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class K2GradleNavigationTest : AbstractKotlinGradleNavigationTest()