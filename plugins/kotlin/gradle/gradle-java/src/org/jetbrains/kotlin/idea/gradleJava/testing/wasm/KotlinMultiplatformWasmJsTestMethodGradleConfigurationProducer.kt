// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.testing.wasm

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.KotlinWasmRunConfigurationDataProvider
import org.jetbrains.kotlin.idea.gradleJava.run.AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.wasm.isWasmJs

class KotlinMultiplatformWasmJsTestMethodGradleConfigurationProducer
    : AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer(), KotlinWasmRunConfigurationDataProvider {
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isWasmJs()
}