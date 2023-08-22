// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.testing.js

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.KotlinJSRunConfigurationDataProvider
import org.jetbrains.kotlin.idea.gradleJava.run.AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isJs

class KotlinMultiplatformJsTestMethodGradleConfigurationProducer
    : AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer(), KotlinJSRunConfigurationDataProvider<Unit>
{
    override val isForTests: Boolean get() = true
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isJs()

    override fun getConfigurationData(context: ConfigurationContext) = Unit
}