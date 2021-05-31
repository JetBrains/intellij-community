// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.testing.js

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.js.KotlinJSRunConfigurationDataProvider
import org.jetbrains.kotlin.idea.run.AbstractKotlinMultiplatformTestClassGradleConfigurationProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.isJs

class KotlinMultiplatformJsTestClassGradleConfigurationProducer
    : AbstractKotlinMultiplatformTestClassGradleConfigurationProducer(), KotlinJSRunConfigurationDataProvider<Unit>
{
    override val isForTests: Boolean get() = true
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isJs()

    override fun getConfigurationData(context: ConfigurationContext) = Unit
    override val hasTestFramework get() = true
}
