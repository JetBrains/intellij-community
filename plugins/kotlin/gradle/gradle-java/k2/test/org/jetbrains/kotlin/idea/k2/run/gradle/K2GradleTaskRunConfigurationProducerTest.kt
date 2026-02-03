// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.run.gradle

import org.jetbrains.kotlin.idea.run.gradle.AbstractKotlinGradleTaskRunConfigurationProducerTest
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
class K2GradleTaskRunConfigurationProducerTest : AbstractKotlinGradleTaskRunConfigurationProducerTest()
