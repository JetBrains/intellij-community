// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.gradle.KotlinGradleGotoDeclarationTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK1PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication

@UseK1PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class K1GradleNavigationTest : KotlinGradleGotoDeclarationTest()