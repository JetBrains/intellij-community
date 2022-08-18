// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.testing.native

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.KotlinNativeRunConfigurationProvider
import org.jetbrains.kotlin.idea.gradleJava.run.AbstractKotlinMultiplatformTestClassGradleConfigurationProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.isNative

class KotlinMultiplatformNativeTestClassGradleConfigurationProducer
    : AbstractKotlinMultiplatformTestClassGradleConfigurationProducer(), KotlinNativeRunConfigurationProvider
{
    override val isForTests: Boolean get() = true
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isNative()
}
